package uk.ac.ebi.fgpt.conan.lsf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.ebi.fgpt.conan.model.ConanParameter;
import uk.ac.ebi.fgpt.conan.model.ConanProcess;
import uk.ac.ebi.fgpt.conan.properties.ConanProperties;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;
import uk.ac.ebi.fgpt.conan.utils.CommandExecutionException;
import uk.ac.ebi.fgpt.conan.utils.ProcessRunner;
import uk.ac.ebi.fgpt.conan.utils.ProcessUtils;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;

/**
 * An abstract {@link uk.ac.ebi.fgpt.conan.model.ConanProcess} that is designed for dispatching operating system
 * processes to the EBI LSF cluster for execution.  You can tailor memory requirements by process.
 *
 * @author Tony Burdett
 * @date 02-Nov-2010
 */
public abstract class AbstractLSFProcess implements ConanProcess {
    private String bsubPath = "bsub";
    private String queueName = "production-rh6";
    private int monitorInterval = 15;
    private String jobName;
    private String lsfOptions;

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    /**
     * Gets the path being used to execute "bsub" commands sent to an LSF cluster.  This may be environment dependent.
     * Defaults to "bsub".
     *
     * @return the bsub path being used to dispatch jobs to an LSF cluster
     */
    protected String getBsubPath() {
        return bsubPath;
    }

    /**
     * Sets the path to use to execute "bsub" commands to dispatch jobs to an LSF cluster.  This may be environment
     * dependent.  Defaults simply to "bsub"
     *
     * @param bsubPath the bsub path to use to dispatch jobs to an LSF cluster
     */
    protected void setBsubPath(String bsubPath) {
        this.bsubPath = bsubPath;
    }

    /**
     * Gets the name of the LSF queue processes should be submitted to when executed.  This may depend on how your LSF
     * cluster is configured.  Defaults to "production".
     *
     * @return the name of the queue to which LSF jobs should be submitted
     */
    protected String getQueueName() {
        return queueName;
    }

    /**
     * Sets the queue to which LSF process should be submitted when executed.  The default is "production", but this may
     * need to be changed based on how your LSF cluster is configured.  If you set this to null, or an empty string,
     * most LSF clusters will submit tasks to the default submission queue.
     *
     * @param queueName the name of the queue to submit to
     */
    protected void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    /**
     * Gets the monitoring interval, in seconds, to use when checking for output from LSF processes.
     *
     * @return the interval between monitoring polls for LSF output, in seconds
     */
    protected int getMonitorInterval() {
        return monitorInterval;
    }

    /**
     * Sets the monitoring interval, in seconds, to use when checking for output from LSF processes.
     *
     * @param monitorInterval the interval between monitoring polls for LSF output, in seconds
     */
    protected void setMonitorInterval(int monitorInterval) {
        this.monitorInterval = monitorInterval;
    }

    /**
     * Gets the LSF job name to use when submitting this job to an LSF cluster.
     *
     * @return the LSF jobname
     */
    protected String getJobName() {
        return jobName;
    }

    /**
     * Sets the LSF job name to use when submitting this job to an LSF cluster
     *
     * @param jobName the LSF jobname
     */
    protected void setJobName(String jobName) {
        this.jobName = jobName;
    }

    /**
     * Executes this process with the supplied parameters.  This implementation executes a process by dispatching it to
     * the EBI LSF cluster, and therefore it's <code>execute</code> method is split into two key parts, {@link
     * #dispatch(String)} and a blocking call to an {@link LSFProcessListener} implementation to monitor the status of
     * the underlying process and block until completion.
     * <p/>
     * The first part governs dispatch - after creation of the command, the command is wrapped inside a call to "bsub"
     * (optionally with memory requirements specified) and executed as a native OS process.  This process then runs
     * independently of this JVM, so if Conan is shutdown the process will continue to run.
     * <p/>
     * The second part governs progress monitoring.  This form of LSF submission is designed to write LSF output to a
     * file, dictated by {@link #getLSFOutputFilePath(java.util.Map)}.  This file is continuously monitored whilst this
     * process is not interrupted until it detects content in this file compatible with a process exit.
     * <p/>
     * The dispatch part of the <code>execute()</code> operation cannot be interrupted - the native OS process should
     * always be generated.  However, the monitoring part can be interrupted and if so, Conan may shutdown without this
     * process being terminated. Therefore, this process always checks for the presence of existing output files when
     * executed.  If an existing output file is found, this process goes into a "recovery" mode of operation.  In this
     * mode, dispatch is skipped and the process goes straight to monitoring, allowing JVM-independent "resumption" of
     * processes.
     * <p/>
     * This method returns true if the task succeeds, false otherwise
     *
     * @param parameters maps parameters to the supplied values required in order to execute a process
     * @return true if the execution completed successfully, false if not
     * @throws ProcessExecutionException if the execution of the process caused an exception
     * @throws IllegalArgumentException  if an incorrect set of parameter values has been supplied, or if required
     *                                   values are null
     * @throws InterruptedException      if the execution of a process is interrupted, which causes it to terminate
     *                                   early
     */
    public boolean execute(Map<ConanParameter, String> parameters)
            throws IllegalArgumentException, ProcessExecutionException, InterruptedException {
        getLog().debug("Executing an LSF process with parameters: " + parameters);
        int memReq = getMemoryRequirement(parameters);
        String lsfOptions = getLSFOptions(parameters);

        // get email address to use as backup in case process fails
        String backupEmail = ConanProperties.getProperty("lsf.backup.email");

        String bsubCommand;
        if (memReq > 0) {
            // generate actual bsub command from template
            bsubCommand =
                    bsubPath + " " +
                            (lsfOptions == null || lsfOptions.equals("") ? "" : lsfOptions + " ") +
                            "-M " + memReq + " " +
                            "-R \"rusage[mem=" + memReq + "]\" " +
                            (getQueueName() == null || getQueueName().equals("") ? "" : "-q " + getQueueName() + " ") +
                            (getJobName() == null || getJobName().equals("") ? "" : "-J " + getJobName() + " ") +
                            "-oo " + getLSFOutputFilePath(parameters) + " " +
                            "-u " + backupEmail + " \"" +
                            getCommand(parameters) + " " +
                            "2>&1\"";
        }
        else {
            // generate actual bsub command from template excluding memory options
            bsubCommand =
                    bsubPath + " " +
                            (lsfOptions == null || lsfOptions.equals("") ? "" : lsfOptions + " ") +
                            (getQueueName() == null || getQueueName().equals("") ? "" : "-q " + getQueueName() + " ") +
                            (getJobName() == null || getJobName().equals("") ? "" : "-J " + getJobName() + " ") +
                            "-oo " + getLSFOutputFilePath(parameters) + " " +
                            "-u " + backupEmail + " \"" +
                            getCommand(parameters) + " " +
                            "2>&1\"";

        }

        // cleanup any old copies of the output files and create a new one
        String lsfOutputFilePath = getLSFOutputFilePath(parameters);
        File lsfOutputFile = new File(lsfOutputFilePath);

        // does an existing output file exist? if so, we need to go into recovery mode
        boolean recoveryMode = lsfOutputFile.exists();

        // only create our output file if we're not in recovery mode
        if (!recoveryMode) {
            getLog().debug("Creating " + lsfOutputFile.getAbsolutePath());
            if (!ProcessUtils.createFiles(lsfOutputFile)) {
                throw new ProcessExecutionException(
                        1,
                        "Unable to create LSF output file at " + lsfOutputFile.getAbsolutePath());
            }
        }

        // set up monitoring of the lsfOutputFile
        InvocationTrackingLSFProcessListener listener = new InvocationTrackingLSFProcessListener();

        final LSFProcessAdapter adapter = new LSFProcessAdapter(lsfOutputFilePath, getMonitorInterval());
        adapter.addLSFProcessListener(listener);

        // process dispatch
        boolean dispatched = false;
        try {
            // only dispatch if we're not in recovery mode
            getLog().debug("In recovery mode? " + recoveryMode);
            if (!recoveryMode) {
                // dispatch the process to the cluster
                getLog().debug("Dispatching command to LSF: " + bsubCommand);
                dispatch(bsubCommand);
            }
            dispatched = true;
        }
        catch (CommandExecutionException e) {
            // could not dispatch to LSF
            getLog().error(
                    "Failed to dispatch job to the LSF cluster (exited with exit code " + e.getExitCode() + ")",
                    e);
            ProcessExecutionException pex = new ProcessExecutionException(
                    e.getExitCode(),
                    "Failed to dispatch job to the LSF cluster (exited with exit code " + e.getExitCode() + ")",
                    e);
            pex.setProcessOutput(e.getErrorOutput());
            try {
                pex.setProcessExecutionHost(InetAddress.getLocalHost().getHostName());
            }
            catch (UnknownHostException e1) {
                getLog().debug("Unknown host", e1);
            }
            throw pex;
        }
        catch (IOException e) {
            getLog().error("Failed to read output stream of native system process");
            getLog().debug("IOException follows", e);
            throw new ProcessExecutionException(1, "Failed to read output stream of native system process", e);
        }
        finally {
            if (!dispatched) {
                // this process didn't start, so delete output files to cleanup before throwing the exception
                getLog().debug("Deleting " + lsfOutputFile.getAbsolutePath());
                ProcessUtils.deleteFiles(lsfOutputFile);
            }
        }

        // process exit value, initialise to -1
        int exitValue = -1;

        // process monitoring
        try {
            getLog().debug("Monitoring process, waiting for completion");
            exitValue = listener.waitFor();
            getLog().debug("LSF Process completed with exit value " + exitValue);

            ProcessExecutionException pex = interpretExitValue(exitValue);
            if (pex == null) {
                return true;
            }
            else {
                pex.setProcessOutput(adapter.getProcessOutput());
                pex.setProcessExecutionHost(adapter.getProcessExecutionHost());
                throw pex;
            }
        }
        finally {
            // this process DID start, so only delete output files to cleanup if the process actually exited,
            // and wasn't e.g. interrupted prior to completion
            if (exitValue != -1) {
                getLog().debug("Deleting " + lsfOutputFile.getAbsolutePath());
                ProcessUtils.deleteFiles(lsfOutputFile);
            }
        }
    }

    /**
     * Creates a native system process from the given command.  This command should be an LSF dispatch command of the
     * form "bsub -oo <file> -q production "<my command>".  The result is an array of strings, where each element
     * represents one line of stdout or stderr for the executed native process.  Usually, this should just be a simple
     * output indicating the machine your process was sent to
     *
     * @param lsfCommand the LSF command to execute
     * @return the stdout.stderr of the process
     * @throws CommandExecutionException if the process exits with a failure condition.  This exception wraps the error
     *                                   output and the process exit code.
     * @throws IOException               if the stdout or stderr of the process could not be read
     */
    protected String[] dispatch(String lsfCommand) throws CommandExecutionException, IOException {
        getLog().debug("Issuing command: [" + lsfCommand + "]");
        ProcessRunner runner = new ProcessRunner();
        runner.redirectStderr(true);
        String[] output = runner.runCommmand(lsfCommand);
        if (output.length > 0) {
            getLog().debug("Response from command [" + lsfCommand + "]: " +
                                   output.length + " lines, first line was " + output[0]);
        }
        return output;
    }

    /**
     * Returns the memory requirements, in MB, for the LSF process that will be dispatched.  By default, this is not
     * used and therefore processes run with environment defaults (8GB for the EBI LSF at the time of writing).  You can
     * override this for more memory hungry (or indeed, less memory hungry!) jobs.
     *
     * @param parameterStringMap the parameters supplied to this process, as this may potentially alter the
     *                           requirements
     * @return the number of MB required to run this process
     */
    protected int getMemoryRequirement(Map<ConanParameter, String> parameterStringMap) {
        return -1;
    }

    /**
     * Returns any custom LSF flags that must be set for the LSF process that will be dispatched.  By default, this is
     * not used and therefore processes run with environment defaults and parameters that can be explicitly set (like
     * queue name, for example).
     * <p/>
     * This is a power user feature for cases when you require fine grained control over the execution options of an LSF
     * command.  Note that if you require multiple -R options, over and above simple memory requirement setting, you
     * should ensure {@link #getMemoryRequirement(java.util.Map)} always returns 0 and manually specify the required -R
     * flag (along with -M if needed).
     *
     * @param parameterStringMap the parameters supplied to this process, as this may potentially alter the
     *                           requirements
     * @return the customized options to set for this LSF command
     */
    protected String getLSFOptions(Map<ConanParameter, String> parameterStringMap) {
        return "";
    }

    /**
     * Translates an exit value returned by the LSF process into a meaningful java exception.  Override this method if
     * you want to do something clever with certain exit values.  The default behaviour is to wrap the supplied exit
     * value inside a ProcessExecutionException and provide a generic error message, if the exit code is non-zero.  If
     * an exit code of zero is passed, this method should return null.
     *
     * @param exitValue the exit value returned from the LSF process upon completion
     * @return a ProcessExecutionException that minimally wraps the exit value of the process, and possibly provides
     *         further informative error messages if the exit value is non-zero, otherwsie null
     */
    protected ProcessExecutionException interpretExitValue(int exitValue) {
        if (exitValue == 0) {
            return null;
        }
        else {
            return new ProcessExecutionException(exitValue);
        }
    }

    /**
     * Returns the name of this component that this process implements, if any.  This is designed to allow
     * implementations of this class to formulate more specific error messages based on some <i>a priori</i> knowledge
     * of what exit codes named applications are likely to return.  If this method returns any value apart from {@link
     * LSFProcess#UNSPECIFIED_COMPONENT_NAME}, the exit code of the process must be used to extract information about
     * the failure condition and a user-meaningful message displayed indicating the error.
     *
     * @return the name of the component this process implements, or LSFProcess.UNSPECIFIED_COMPONENT_NAME if something
     *         else.
     */
    protected abstract String getComponentName();

    /**
     * The script to run on the LSF.  This does not need to be a bsub command, just the commands you wish this
     * ConanProcess to execute.  Often, this will simply be the script to run
     *
     * @param parameters the parameters supplied to this ConanProcess
     * @return the commands to run on the LSF
     * @throws IllegalArgumentException if the parameters supplied were invalid
     */
    protected abstract String getCommand(Map<ConanParameter, String> parameters)
            throws IllegalArgumentException;

    /**
     * The output file LSF output should be written to.  Each invocation of a ConanProcess with the given Conan
     * parameter values should read/write to the same file to ensure it is possible to recover monitoring for
     * re-executed tasks.
     *
     * @param parameters the parameters supplied to this ConanProcess
     * @return the File that the LSF process should write it's output to
     * @throws IllegalArgumentException if the parameters supplied were invalid
     */
    protected abstract String getLSFOutputFilePath(Map<ConanParameter, String> parameters)
            throws IllegalArgumentException;

    /**
     * An {@link LSFProcessListener} that encapsulates the state of each invocation of a process and updates flags for
     * completion and success.  Processes using this listener implementation can block on {@link #waitFor()}, which only
     * returns once the LSF process being listened to is complete.
     */
    private class InvocationTrackingLSFProcessListener implements LSFProcessListener {
        private boolean complete;
        private int exitValue;

        private InvocationTrackingLSFProcessListener() {
            complete = false;
            exitValue = -1;
        }

        public void processComplete(LSFProcessEvent evt) {
            getLog().debug("File finished writing, process exit value was " + evt.getExitValue());
            exitValue = evt.getExitValue();
            complete = true;
            synchronized (this) {
                notifyAll();
            }
        }

        public void processUpdate(LSFProcessEvent evt) {
            // do nothing, not yet finished
            getLog().debug("File was modified");
            synchronized (this) {
                notifyAll();
            }
        }

        public void processError(LSFProcessEvent evt) {
            // something went wrong
            getLog().debug("File was deleted by an external process");
            exitValue = 1;
            complete = true;
            synchronized (this) {
                notifyAll();
            }
        }

        /**
         * Returns the success of the LSFProcess being listened to, only once complete.  This method blocks until
         * completion or an interruption occurs.
         *
         * @return the exit value of the underlying process
         * @throws InterruptedException if the thread was interrupted whilst waiting
         */
        public int waitFor() throws InterruptedException {
            while (!complete) {
                synchronized (this) {
                    wait();
                }
            }
            getLog().debug("Process completed: exitValue = " + exitValue);
            return exitValue;
        }
    }
}
