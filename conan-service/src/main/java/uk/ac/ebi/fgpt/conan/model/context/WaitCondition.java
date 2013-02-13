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

import uk.ac.tgac.rampart.conan.conanx.exec.context.scheduler.ExitStatus;
import uk.ac.tgac.rampart.conan.conanx.exec.context.scheduler.ExitStatusType;

/**
 * User: maplesod
 * Date: 10/01/13
 * Time: 17:38
 */
public interface WaitCondition {

    ExitStatus createExitStatus(ExitStatusType exitStatusType);

    ExitStatus getExitStatus();

    void setExitStatus(ExitStatus exitStatus);

    String getCondition();

    void setCondition(String condition);

    String getCommand();
}
