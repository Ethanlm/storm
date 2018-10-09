/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.  The ASF licenses this file to you under the Apache License, Version
 * 2.0 (the "License"); you may not use this file except in compliance with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package org.apache.storm.container;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.storm.daemon.supervisor.ExitCodeCallback;

/**
 * A plugin to support resource isolation and limitation within Storm.
 */
public interface ResourceIsolationInterface {

    /**
     * Called when starting up
     *
     * @param conf the cluster config
     * @throws IOException on any error.
     */
    void prepare(Map<String, Object> conf) throws IOException;

    /**
     * This function should be used prior to starting the worker to reserve resources for the worker.
     *
     * @param workerId worker id of the worker to start
     * @param workerMemory the amount of memory for the worker or null if not enforced
     * @param workerCpu the amount of cpu for the worker or null if not enforced
     */
    void reserveResourcesForWorker(String workerId, Integer workerMemory, Integer workerCpu);

    /**
     * This function will be called when the worker needs to shutdown. This function should include logic to clean up
     * after a worker is shutdown.
     *
     * @param workerId worker id to shutdown and clean up after
     */
    void releaseResourcesForWorker(String workerId);

    /**
     * After reserving resources for the worker (i.e. calling reserveResourcesForWorker),
     * this function can be used to launch worker process.
     * @param user                the user who runs the command as
     * @param workerId            the Id of the worker
     * @param command             the command to run
     * @param env                 the environment to run the command
     * @param logPrefix           the prefix to include in the logs
     * @param processExitCallback a callback for when the process exits
     * @param targetDir           the working directory to run the command in
     * @throws IOException
     */
    void launchWorkerProcess(String user, String workerId, List<String> command, Map<String, String> env,
                             String logPrefix, ExitCodeCallback processExitCallback, File targetDir) throws IOException;

    /**
     * Get the current memory usage of the a given worker.
     *
     * @param workerId the id of the worker
     * @return the amount of memory the worker is using in bytes or -1 if not supported
     * @throws IOException on any error.
     */
    long getMemoryUsage(String workerId) throws IOException;

    /**
     * @return The amount of memory in bytes that are free on the system. This might not be the entire box, it might be
     *     within a parent resource group.
     * @throws IOException on any error.
     */
    long getSystemFreeMemoryMb() throws IOException;

    /**
     * Kill the given worker.
     * @param user the user that the worker is running as
     * @param workerId the id of the worker to kill
     * @throws IOException on any error
     */
    void kill(String user, String workerId) throws IOException;

    /**
     * Kill the given worker forcefully
     * @param user the user that the worker is running as
     * @param workerId the id of the worker to kill
     * @throws IOException on any error
     */
    void forceKill(String user, String workerId) throws IOException;

    /**
     * Check if all the processes are dead.
     * @param user the user that the processes are running as
     * @param workerId the id of the worker to kill
     * @return true if all the processed are dead; false otherwise
     * @throws IOException on any error
     */
    boolean areAllProcessesDead(String user, String workerId) throws IOException;

    /**
     * Run profiling command.
     * @param user the user that the worker is running as
     * @param workerId the id of the worker
     * @param command the command to run
     * @param env the environment to run the command
     * @param logPrefix the prefix to include in the logs
     * @param targetDir the working directory to run the command in
     * @return true if succeeds; false otherwise
     * @throws IOException on any error
     * @throws InterruptedException if interrupted
     */
    boolean runProfilingCommand(String user, String workerId, List<String> command, Map<String, String> env,
                                String logPrefix, File targetDir) throws IOException, InterruptedException;

    /**
     * Return true if resources are being managed.
     * The {@link DefaultResourceIsolationManager} will have it return false since it doesn't really manage resources.
     * @return true if resources are being managed.
     */
    boolean isResourceManaged();
}
