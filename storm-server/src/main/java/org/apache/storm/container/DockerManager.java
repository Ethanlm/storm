package org.apache.storm.container;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.storm.Config;
import org.apache.storm.DaemonConfig;
import org.apache.storm.container.cgroup.CgroupManager;
import org.apache.storm.container.cgroup.CgroupUtils;
import org.apache.storm.container.cgroup.SubSystemType;
import org.apache.storm.container.cgroup.core.MemoryCore;
import org.apache.storm.daemon.supervisor.ClientSupervisorUtils;
import org.apache.storm.daemon.supervisor.ExitCodeCallback;
import org.apache.storm.shade.com.google.common.io.Files;
import org.apache.storm.shade.org.apache.commons.lang.StringUtils;
import org.apache.storm.utils.ConfigUtils;
import org.apache.storm.utils.ObjectReader;
import org.apache.storm.utils.ServerUtils;
import org.apache.storm.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerManager implements ResourceIsolationInterface {
    private static final Logger LOG = LoggerFactory.getLogger(DockerManager.class);
    private Map<String, Object> conf;
    private boolean runAsUser;
    private Integer cpuNum;
    private Integer memoryLimitMB;

    @Override
    public void prepare(Map<String, Object> conf) throws IOException {
        this.conf = conf;
        runAsUser = ObjectReader.getBoolean(conf.get(Config.SUPERVISOR_RUN_WORKER_AS_USER), false);
    }

    @Override
    public void reserveResourcesForWorker(String workerId, Integer workerMemory, Integer workerCpu) {
        cpuNum = workerCpu;
        if (conf.get(DaemonConfig.STORM_WORKER_CGROUP_CPU_LIMIT) != null) {
            cpuNum = ((Number) conf.get(DaemonConfig.STORM_WORKER_CGROUP_CPU_LIMIT)).intValue();
        }
        LOG.info("cpuNum:{}", cpuNum);

        if ((boolean) this.conf.get(DaemonConfig.STORM_CGROUP_MEMORY_ENFORCEMENT_ENABLE)) {
            memoryLimitMB = workerMemory;
        }
    }

    @Override
    public void releaseResourcesForWorker(String workerId) {
        //NO-OP
    }

    @Override
    public void launchWorkerProcess(String user, String workerId, List<String> command, Map<String, String> env,
                                    String logPrefix, ExitCodeCallback processExitCallback, File targetDir) throws IOException {
        String workerDir = targetDir.getAbsolutePath();
        String scriptPath = ServerUtils.writeScript(workerDir, command, env);

        String cidFilePath = workerDir + File.separator + "container.cid";

        List<String> dockerCommand = new ArrayList<>(Arrays.asList("docker", "run",
            "--name=" + workerId,
            "-d",
            "--net=host",
            "--cgroup-parent=storm.slice",
            "--workdir=" + targetDir,
            "--cidfile=" + cidFilePath,
            "-v", "/tmp/apache-storm-2.0.0-SNAPSHOT/:/tmp/apache-storm-2.0.0-SNAPSHOT/"));

        if (cpuNum != null) {
            dockerCommand.add("--cpus=" + cpuNum / 100.0);
        }
        if (memoryLimitMB != null) {
            dockerCommand.add("--memory=" + memoryLimitMB + "m");
        }
        dockerCommand.addAll(Arrays.asList(
            "docker.ouroath.com:4443/hadoop/docker_configs/rhel6:20180918-215129",
            "bash", scriptPath));

        LOG.info("docker command: {}", dockerCommand);

        //TODO
        //currently didn't find a good way to use processExitCallback.
        //Because this is launching container and the command will return after launching.
        ClientSupervisorUtils.runDockerCommand(conf, dockerCommand, null, logPrefix, null, targetDir);
    }

    @Override
    public long getMemoryUsage(String workerId) throws IOException {
        String workerDir = ConfigUtils.workerRoot(conf, workerId);
        String cidFilePath = workerDir + File.separator + "container.cid";

        String cid = Files.readLines(new File(cidFilePath), Charset.defaultCharset()).get(0);

        LOG.info("cidFilePath: {}, cid={}", cidFilePath, cid);

        String memoryCgroupPath = "/sys/fs/cgroup/memory/storm.slice/docker-" + cid + ".scope/memory.usage_in_bytes";

        LOG.info("memoryCgroupPath: {}", memoryCgroupPath);

        Long memoryUsage = Long.parseLong(Files.readLines(new File(memoryCgroupPath), Charset.defaultCharset()).get(0));

        LOG.info("memoryUsage: {}", memoryUsage);

        return memoryUsage;
    }

    @Override
    public long getSystemFreeMemoryMb() throws IOException {
        long rootCgroupLimitFree = Long.MAX_VALUE;
        try {
            String memoryCgroupPath = "/sys/fs/cgroup/memory/storm.slice";
            //For cgroups no limit is max long.
            long limit = Long.parseLong(CgroupUtils.readFileByLine(memoryCgroupPath + "/memory.limit_in_bytes").get(0));
            long used = Long.parseLong(CgroupUtils.readFileByLine(memoryCgroupPath + "/memory.max_usage_in_bytes").get(0));
            rootCgroupLimitFree = (limit - used) / 1024 / 1024;
        } catch (FileNotFoundException e) {
            //Ignored if cgroups is not setup don't do anything with it
        }

        long res = Long.min(rootCgroupLimitFree, Utils.getMemInfoFreeMb());
        LOG.info("getSystemFreeMemoryMb: {}", res);

        return res;
    }

    @Override
    public void kill(String workerId, String user) throws IOException {
        String workerDir = ConfigUtils.workerRoot(conf, workerId);
        List<String> dockerCommand = Arrays.asList("docker", "rm", "-f", workerId);
        ClientSupervisorUtils.runDockerCommand(conf, dockerCommand, null, null, null, new File(workerDir));
    }

    @Override
    public void forceKill(String workerId, String user) throws IOException {
        kill(workerId, user);
    }

    @Override
    public boolean areAllProcessesDead(String workerId, String userOfProcess) throws IOException {
        String workerDir = ConfigUtils.workerRoot(conf, workerId);
        List<String> dockerCommand = Arrays.asList("docker", "inspect", workerId);
        Process p = ClientSupervisorUtils.runDockerCommand(conf, dockerCommand, null, null, null, new File(workerDir));
        try {
            p.waitFor();
            InputStream inputStream = p.getInputStream();
            String line;
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
                while ((line = bufferedReader.readLine()) != null) {
                    if (line.contains("Error: No such object:")) {
                        return true;
                    }
                }
            }
        } catch (InterruptedException e) {
            LOG.info("interrupted: {}", e.getStackTrace());
            return true;
        }
        return false;
    }

    @Override
    public boolean checkMemory() {
        return true;
    }

    @Override
    public boolean runProfilingCommand(String workerId, String user, List<String> command, Map<String, String> env, String logPrefix, File targetDir) throws IOException, InterruptedException {
        String workerDir = ConfigUtils.workerRoot(conf, workerId);
        List<String> dockerCommand = Arrays.asList("docker", "exec", workerId);
        dockerCommand.addAll(command);
        Process p = ClientSupervisorUtils.runDockerCommand(conf, dockerCommand, null, null, null, new File(workerDir));
        p.waitFor();
        if (p.exitValue() == 0) {
            return true;
        } else {
            return false;
        }
    }
}
