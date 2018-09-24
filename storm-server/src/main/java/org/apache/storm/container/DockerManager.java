package org.apache.storm.container;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.storm.Config;
import org.apache.storm.daemon.supervisor.ClientSupervisorUtils;
import org.apache.storm.daemon.supervisor.ExitCodeCallback;
import org.apache.storm.utils.ObjectReader;
import org.apache.storm.utils.ServerUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerManager implements ResourceIsolationInterface {
    private static final Logger LOG = LoggerFactory.getLogger(DockerManager.class);
    private Map<String, Object> conf;
    private boolean runAsUser;
    private String runDockerAsUser = "root";

    @Override
    public void prepare(Map<String, Object> conf) throws IOException {
        this.conf = conf;
        runAsUser = ObjectReader.getBoolean(conf.get(Config.SUPERVISOR_RUN_WORKER_AS_USER), false);
    }

    @Override
    public void reserveResourcesForWorker(String workerId, Integer workerMemory, Integer workerCpu) {

    }

    @Override
    public void releaseResourcesForWorker(String workerId) {

    }

    @Override
    public void launchWorkerProcess(String user, String workerId, List<String> command, Map<String, String> env,
                                    String logPrefix, ExitCodeCallback processExitCallback, File targetDir) throws IOException {
        List<String> dockerCommand = Arrays.asList("docker",
            "--name=" + workerId,
            "-d",
            "--net='host'",
            "--cap-drop='ALL'",
            "--read-only",
            "--security-opt", "'no-new-privileges'",
            "-v", "'/sys/fs/cgroup:/sys/fs/cgroup:ro'",
            " -v", "'/var/run/nscd:/var/run/nscd'",

            "--workerdir=" + targetDir);
        dockerCommand.add("docker");



    }

    @Override
    public long getMemoryUsage(String workerId) throws IOException {
        return 0;
    }

    @Override
    public long getSystemFreeMemoryMb() throws IOException {
        return 0;
    }

    @Override
    public void kill(String workerId, String user) throws IOException {

    }

    @Override
    public void forceKill(String workerId, String user) throws IOException {

    }

    @Override
    public boolean areAllProcessesDead(String workerId, String userOfProcess) throws IOException {
        return false;
    }

    @Override
    public boolean checkMemory() {
        return true;
    }

    @Override
    public boolean runProfilingCommand(String user, List<String> command, Map<String, String> env, String logPrefix, File targetDir) throws IOException, InterruptedException {
        return false;
    }
}
