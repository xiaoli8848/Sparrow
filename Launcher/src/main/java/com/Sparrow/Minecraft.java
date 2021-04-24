package com.Sparrow;

import org.to2mbn.jmccc.option.MinecraftDirectory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.jar.JarFile;

/**
 * 游戏版本类，表示 {@code .minecraft/versions/} 下的所有游戏版本。
 * 本类类似于 {@link MinecraftDirectory} ，且本类工厂方法参数含有 {@link MinecraftDirectory} ，但两者没有继承关系，本类封装了它的一些功能。
 */
public class Minecraft {
    public String path;
    public String version;
    public String rootPath;
    public ArrayList<save> saves;
    public ArrayList<mod> mods;

    /**
     * 为本类工厂方法和子类等提供私有无参构造函数，一般为private或protected。
     */
    protected Minecraft() {
    }

    /**
     * 获取游戏目录下所有版本的版本号。
     *
     * @param dir 游戏根目录
     * @return {@link ArrayList<String>}，含有所有版本的版本号。
     */
    public static ArrayList<String> getMinecraftVersions(MinecraftDirectory dir) {
        //Objects.requireNonNull(dir);
        ArrayList<String> versions = new ArrayList<>();
        File[] subdirs = dir.getVersions().listFiles();
        if (subdirs != null) {
            for (File file : subdirs) {
                if (file.isDirectory() && doesVersionExist(dir, file.getName())) {
                    versions.add(file.getName());
                }
            }
        }
        return versions;
    }

    /**
     * 检测游戏目录下是否有指定版本。
     *
     * @param minecraftDir 游戏目录
     * @param version      游戏版本
     * @return boolean，目录下是否有指定版本。
     */
    public static boolean doesVersionExist(MinecraftDirectory minecraftDir, String version) {
        return minecraftDir.getVersionJson(version).isFile();
    }

    /**
     * 获取游戏根目录下所有游戏版本。
     *
     * @param dir 游戏根目录
     * @return 一个 {@link Minecraft} 类型的数组，表示游戏根目录下所有的游戏版本。
     */
    public static Minecraft[] getMinecrafts(MinecraftDirectory dir) {
        ArrayList<String> versions = new ArrayList<>(getMinecraftVersions(dir));
        ArrayList<Minecraft> result = new ArrayList<>();
        for (String s : versions) {
            Minecraft temp = new Minecraft();
            temp.version = s;
            temp.path = dir.getRoot().toString() + File.separator + "versions" + File.separator + s + File.separator;
            temp.rootPath = dir.getRoot().getPath();
            temp.saves = temp.getSaves(temp.rootPath);
            temp.mods = temp.getMods(temp.rootPath);
            result.add(temp);
        }
        return result.toArray(new Minecraft[versions.size()]);
    }

    @Override
    public String toString() {
        return this.version + " - " + path;
    }

    public void launchOffline(String playername, boolean debug, boolean FC, int minMem, int maxMem, int width, int height, String serverURL) {
        launcher.launch_offline(rootPath, version, playername, debug, FC, minMem, maxMem, width, height, serverURL);
    }

    public MinecraftDirectory toMinecraftDirectory() {
        return new MinecraftDirectory(this.rootPath);
    }

    /**
     * 存档类，表示 {@code .minecraft/saves/}下的存档。
     */
    public class save extends File {
        public String name;
        public File image;

        private save(String path) {
            super(path);
        }
    }

    protected ArrayList<save> getSaves(String rootDir) {
        File[] saves = new File(rootDir + File.separator + "saves" + File.separator).listFiles();
        ArrayList<save> temp = new ArrayList<>();
        if(saves == null){
            return new ArrayList<>();
        }
        for (File save : saves) {
            save t = new save(save.toString());
            t.name = save.getName();
            t.image = new File(save.toString() + File.separator + "icon.png");
            temp.add(t);
        }
        return temp;
    }

    public class mod extends File {
        public String name;

        private mod(String jarPath) throws IOException {
            super(jarPath);
            this.name = this.getName().substring(0, this.getName().lastIndexOf(".jar"));
        }
    }

    protected ArrayList<mod> getMods(String rootDir) {
        File[] mods = new File(rootDir + File.separator + "mods" + File.separator).listFiles();
        ArrayList<mod> temp = new ArrayList<>();
        if(mods == null){
            return new ArrayList<>();
        }
        for (File mod : mods) {
            try {
                temp.add(new mod(mod.toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return temp;
    }
}
