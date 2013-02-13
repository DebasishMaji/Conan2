package uk.ac.ebi.fgpt.conan.service;

import uk.ac.ebi.fgpt.conan.dao.ConanProcessDAO;
import uk.ac.ebi.fgpt.conan.model.ConanProcess;
import uk.ac.ebi.fgpt.conan.model.context.ExecutionContext;
import uk.ac.ebi.fgpt.conan.model.context.Locality;
import uk.ac.ebi.fgpt.conan.model.context.Scheduler;
import uk.ac.ebi.fgpt.conan.model.context.WaitCondition;
import uk.ac.ebi.fgpt.conan.model.monitor.ProcessAdapter;
import uk.ac.ebi.fgpt.conan.service.exception.ProcessExecutionException;

import java.util.Collection;

/**
 * Simple implementation of a process service that delegates lookup calls to a process DAO.
 *
 * @author Tony Burdett
 * @date 25-Nov-2010
 */
public class DefaultProcessService implements ConanProcessService {
    private ConanProcessDAO processDAO;

    public ConanProcessDAO getProcessDAO() {
        return processDAO;
    }

    public void setProcessDAO(ConanProcessDAO processDAO) {
        this.processDAO = processDAO;
    }

    public Collection<ConanProcess> getAllAvailableProcesses() {
        return getProcessDAO().getProcesses();
    }

    public ConanProcess getProcess(String processName) {
        return getProcessDAO().getProcess(processName);
    }

    @Override
    public int execute(ConanProcess process, ExecutionContext executionContext)
            throws InterruptedException, ProcessExecutionException {

        return this.execute(process.getFullCommand(), executionContext);
    }

    @Override
    public int execute(String command, ExecutionContext executionContext) throws InterruptedException, ProcessExecutionException {

        Locality locality = executionContext.getLocality();

        if (!locality.establishConnection()) {
            throw new ProcessExecutionException(-1, "Could not establish connection to the terminal.  Command " +
                    command + " will not be submitted.");
        }

        int exitCode = -1;

        if (executionContext.usingScheduler()) {

            Scheduler scheduler = executionContext.getScheduler();

            String commandToExecute = scheduler.createCommand(command);

            if (executionContext.isForegroundJob()) {

                exitCode = locality.monitoredExecute(commandToExecute, scheduler.createProcessAdapter());
            } else {
                locality.dispatch(command);
                exitCode = 0; // Doesn't return an exit code, so if there were no exceptions assume everything went well
            }
        } else {

            if (executionContext.isForegroundJob()) {
                locality.execute(command);
            } else {
                throw new UnsupportedOperationException("Can't dispatch simple commands yet");
            }
        }


        if (!locality.disconnect()) {
            throw new ProcessExecutionException(-1, "Command was submitted but could not disconnect the terminal session.  Future jobs may not work.");
        }

        return exitCode;
    }


    @Override
    public int waitFor(WaitCondition waitCondition, ExecutionContext executionContext) throws InterruptedException, ProcessExecutionException {

        if (!executionContext.usingScheduler()) {
            throw new UnsupportedOperationException("Can't wait for non-scheduled tasks yet");
        }

        Scheduler scheduler = executionContext.getScheduler();

        String waitCommand = scheduler.createWaitCommand(waitCondition);

        ProcessAdapter processAdapter = scheduler.createProcessAdapter();

        return executionContext.getLocality().monitoredExecute(waitCommand, processAdapter);
    }
}
