/**
 * RAMPART - Robust Automatic MultiPle AssembleR Toolkit
 * Copyright (C) 2013  Daniel Mapleson - TGAC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **/
package uk.ac.ebi.fgpt.conan.core.context.scheduler.pbs;

import uk.ac.ebi.fgpt.conan.core.context.scheduler.AbstractScheduler;
import uk.ac.ebi.fgpt.conan.model.context.ExitStatusType;
import uk.ac.ebi.fgpt.conan.model.context.Scheduler;
import uk.ac.ebi.fgpt.conan.model.context.WaitCondition;
import uk.ac.ebi.fgpt.conan.model.monitor.ProcessAdapter;
import uk.ac.tgac.rampart.conan.conanx.exec.context.WaitCondition;
import uk.ac.tgac.rampart.conan.conanx.exec.context.scheduler.AbstractScheduler;
import uk.ac.tgac.rampart.conan.conanx.exec.context.scheduler.ExitStatusType;
import uk.ac.tgac.rampart.conan.conanx.exec.context.scheduler.Scheduler;
import uk.ac.tgac.rampart.conan.conanx.exec.process.monitor.ProcessAdapter;

import java.io.File;

public class PBSScheduler extends AbstractScheduler {

    public static final String QSUB = "qsub";

    public PBSScheduler() {
        this(new PBSArgs());
    }

    public PBSScheduler(PBSArgs args) {
        super(QSUB, args);
    }

    @Override
    public ProcessAdapter createTaskAdapter(File monitorFile, int monitorInterval) {
        return null;
    }

    @Override
    public String createCommand(String internalCommand) {
        return internalCommand;
    }

    @Override
    public String createWaitCommand(WaitCondition waitCondition) {
        return null;
    }

    @Override
    public String createKillCommand(String jobId) {
        return "qdel " + jobId;
    }

    @Override
    public WaitCondition createWaitCondition(ExitStatusType exitStatus, String condition) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Scheduler copy() {
        //TODO Not too nice... shouldn't really use casting here but it will always give the right result.  To tidy up late.
        return new PBSScheduler(new PBSArgs((PBSArgs) this.getArgs()));
    }

}
