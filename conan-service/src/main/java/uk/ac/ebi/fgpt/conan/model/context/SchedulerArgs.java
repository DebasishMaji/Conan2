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
package uk.ac.ebi.fgpt.conan.model.context;

import java.io.File;

/**
 * This is just a marker interface.  All we really need implemented is the toString method.
 *
 * @author maplesod
 */
public interface SchedulerArgs {

    SchedulerArgs copy();

    int getMemoryMB();

    void setMemoryMB(int memoryMB);

    int getThreads();

    void setThreads(int threads);

    String getJobName();

    void setJobName(String jobName);

    String getQueueName();

    void setQueueName(String queueName);

    File getMonitorFile();

    void setMonitorFile(File monitorFile);

    boolean isBackgroundTask();

    void setBackgroundTask(boolean state);

    int getMonitorInterval();
}
