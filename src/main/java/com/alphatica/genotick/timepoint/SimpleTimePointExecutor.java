package com.alphatica.genotick.timepoint;

import com.alphatica.genotick.genotick.*;
import com.alphatica.genotick.population.*;
import com.alphatica.genotick.processor.RobotExecutorFactory;
import com.alphatica.genotick.ui.UserOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

class SimpleTimePointExecutor implements TimePointExecutor {

    private final ExecutorService executorService;
    private final UserOutput output;
    private DataSetExecutor dataSetExecutor;
    private RobotExecutorFactory robotExecutorFactory;


    public SimpleTimePointExecutor(UserOutput output) {
        int cores = Runtime.getRuntime().availableProcessors();
        this.output = output;
        executorService = Executors.newFixedThreadPool(cores * 2, new DaemonThreadFactory());
    }

    @Override
    public Map<RobotName, List<RobotResult>> execute(List<RobotData> robotDataList,
                                                     Population population, boolean updateRobots, boolean requireSymmetrical) {
        if(robotDataList.isEmpty())
            return Collections.emptyMap();
        List<Future<List<RobotResult>>> tasks = submitTasks(robotDataList,population,updateRobots);
        return getResults(tasks, requireSymmetrical);
    }

    private Map<RobotName, List<RobotResult>> getResults(List<Future<List<RobotResult>>> tasks, boolean requireSymmetrical) {
        Map<RobotName, List<RobotResult>> resultMap = new HashMap<>();
        while(!tasks.isEmpty()) {
            try {
                List<RobotResult> results = getRobotResults(tasks);
                if(!requireSymmetrical || resultsSymmetrical(results)) {
                    updateMap(resultMap, results);
                }
            } catch (InterruptedException ignore) {
                /* Do nothing, try again */
            } catch (ExecutionException e) {
                output.errorMessage(e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return resultMap;
    }

    private void updateMap(Map<RobotName, List<RobotResult>> map, List<RobotResult> results) {
        List<RobotResult> robotResults = map.computeIfAbsent(results.get(0).getName(), n -> new ArrayList<>());
        robotResults.addAll(results);
    }

    private List<RobotResult> getRobotResults(List<Future<List<RobotResult>>> tasks) throws InterruptedException, ExecutionException {
        int lastIndex = tasks.size() - 1;
        Future<List<RobotResult>> future = tasks.get(lastIndex);
        List<RobotResult> results = future.get();
        tasks.remove(lastIndex);
        return results;
    }

    private boolean resultsSymmetrical(List<RobotResult> results) {
        long votesUp = results.stream().filter(result -> result.getPrediction() == Prediction.UP).count();
        long votesDown = results.stream().filter(result -> result.getPrediction() == Prediction.DOWN).count();
        return votesUp == votesDown;
    }

    @Override
    public void setSettings(DataSetExecutor dataSetExecutor, RobotExecutorFactory robotExecutorFactory) {
        this.dataSetExecutor = dataSetExecutor;
        this.robotExecutorFactory = robotExecutorFactory;
    }

    /*
       Return type here is as ugly as it gets and I'm not proud. However, it seems to be the quickest.
       */
    private List<Future<List<RobotResult>>> submitTasks(List<RobotData> robotDataList,
                                                        Population population,
                                                        boolean updateRobots) {
        List<Future<List<RobotResult>>> tasks = new ArrayList<>();
        population.listRobotsNames().forEach(robotName -> {
            Task task = new Task(robotName, robotDataList, population, updateRobots);
            Future<List<RobotResult>> future = executorService.submit(task);
            tasks.add(future);
        });
        return tasks;
    }

    private class Task implements Callable<List<RobotResult>> {

        private final RobotName robotName;
        private final List<RobotData> robotDataList;
        private final Population population;
        private final boolean updateRobots;

        public Task(RobotName robotName, List<RobotData> robotDataList, Population population, boolean updateRobots) {
            this.robotName = robotName;
            this.robotDataList = robotDataList;
            this.population = population;
            this.updateRobots = updateRobots;
        }

        @Override
        public List<RobotResult> call() throws Exception {
            Robot robot = population.getRobot(robotName);
            List<RobotResult> list = dataSetExecutor.execute(robotDataList, robot, robotExecutorFactory);
            if(updateRobots) {
                updateRobots(robot,list);
            }
            return list;
        }

        private void updateRobots(Robot robot, List<RobotResult> list) {
            for(RobotResult result: list) {
                robot.recordPrediction(result.getPrediction());
            }
            population.saveRobot(robot);
        }
    }
}

