package com.alphatica.genotick.population;

import com.alphatica.genotick.genotick.RandomGenerator;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class PopulationDAOFileSystem implements PopulationDAO {
    private static final String FILE_EXTENSION = ".prg";
    private final String robotsPath;
    private final Random random;
    private final List<RobotName> names;

    public PopulationDAOFileSystem(String path) {
        checkPath(path);
        robotsPath = path;
        names = loadRobotNames();
        random = RandomGenerator.get();
    }

    @Override
    public Robot getRobotByName(RobotName name) {
        File file = createFileForName(name);
        return getRobotFromFile(file);
    }

    @Override
    public Stream<Robot> getRobots() {
        return names.stream().map(this::getRobotByName);
    }

    @Override
    public Stream<RobotName> getRobotNames() {
        return names.stream();
    }

    @Override
    public Iterable<Robot> getRobotList() {
        return getRobotList(0, 0);
    }

    @Override
    public Iterable<Robot> getRobotList(int fromIndex, int toIndex) {
        return new Iterable<Robot>() {
            class ListAvailableRobots implements Iterator<Robot> {
                private final List<RobotName> names;
                int index = 0;
                ListAvailableRobots(int fromIndex, int toIndex) {
                    List<RobotName> names = getAllRobotsNames();
                    this.names = (fromIndex < toIndex && fromIndex >= 0 && toIndex < names.size())
                            ? getAllRobotsNames().subList(fromIndex, toIndex)
                            : getAllRobotsNames();
                }
                @Override
                public boolean hasNext() {
                    return names.size() > index;
                }

                @Override
                public Robot next() {
                    if(!hasNext()) {
                        throw new NoSuchElementException(format("Unable to get element %d", index+1));
                    }
                    return getRobotByName(names.get(index++));
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException("remove() not supported");
                }
            }
            @Override
            public Iterator<Robot> iterator() {
                return new ListAvailableRobots(fromIndex, toIndex);
            }
        };
    }

    @Override
    public int getAvailableRobotsCount() {
        return getAllRobotsNames().size();
    }

    @Override
    public void saveRobot(Robot robot) {
        if(robot.getName() == null) {
            RobotName name = getAvailableName();
            robot.setName(name);
            names.add(name);
        }
        File file = createFileForName(robot.getName());
        saveRobotToFile(robot,file);
    }

    @Override
    public void removeRobot(RobotName robotName) {
        File file = createFileForName(robotName);
        names.remove(robotName);
        boolean result = file.delete();
        if(!result)
            throw new DAOException("Unable to remove file " + file.getAbsolutePath());
    }

    @Override
    public void removeAllRobots() {
        Set<RobotName> tmpNames = new HashSet<>(names);
        tmpNames.forEach(this::removeRobot);
    }

    public static Robot getRobotFromFile(File file) {
        try(ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            return (Robot) ois.readObject();
        } catch (ClassNotFoundException | IOException e) {
            throw new DAOException(e);
        }
    }

    private List<RobotName> loadRobotNames() {
        String [] files = listFiles(robotsPath);
        return Arrays.stream(files).map(file -> {
            String longString = file.substring(0,file.indexOf('.'));
            return new RobotName(Long.parseLong(longString));
        }).collect(Collectors.toList());
    }

    private void checkPath(String dao) {
        File file = new File(dao);
        if(!file.exists())
            throw new DAOException(format("Path '%s' does not exist.",dao));
        if(!file.isDirectory())
            throw new DAOException(format("Path '%s' is not a directory.",dao));
        if(!file.canRead())
            throw new DAOException(format("Path '%s' is not readable.",dao));
    }

    private List<RobotName> getAllRobotsNames() {
        List<RobotName> list = new ArrayList<>();
        String [] fileList = listFiles(robotsPath);
        if(fileList == null)
            return list;
        for(String name: fileList) {
            String shortName = name.split("\\.")[0];
            Long l = Long.parseLong(shortName);
            list.add(new RobotName(l));
        }
        return list;
    }

    private String [] listFiles(String dir) {
        File path = new File(dir);
        return path.list((dir1, name) -> name.endsWith(FILE_EXTENSION));
    }

    private RobotName getAvailableName() {
        File file;
        long l;
        do {
            l = Math.abs(random.nextLong() - 1);
            file = new File(robotsPath + String.valueOf(l) + FILE_EXTENSION);
        } while (file.exists());
        return new RobotName(l);
    }

    private File createFileForName(RobotName name) {
        return new File(robotsPath + File.separator + name.toString() + FILE_EXTENSION);
    }

    private void saveRobotToFile(Robot robot, File file)  {
        deleteFileIfExists(file);
        try(ObjectOutputStream ous = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
            ous.writeObject(robot);
        } catch (IOException ex) {
            throw new DAOException(ex);
        }
    }

    private void deleteFileIfExists(File file) {
        if(!file.exists())
            return;
        if(!file.delete()) {
            throw new DAOException("Unable to delete file: " + file);
        }
    }
}
