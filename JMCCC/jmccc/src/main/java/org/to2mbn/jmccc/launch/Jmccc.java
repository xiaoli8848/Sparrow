package org.to2mbn.jmccc.launch;

import org.json.JSONObject;
import org.to2mbn.jmccc.auth.AuthInfo;
import org.to2mbn.jmccc.exec.DaemonStreamPumpMonitor;
import org.to2mbn.jmccc.exec.GameProcessListener;
import org.to2mbn.jmccc.exec.LoggingMonitor;
import org.to2mbn.jmccc.exec.ProcessMonitor;
import org.to2mbn.jmccc.option.LaunchOption;
import org.to2mbn.jmccc.option.MinecraftDirectory;
import org.to2mbn.jmccc.util.FileUtils;
import org.to2mbn.jmccc.version.*;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Jmccc implements Launcher {

    private boolean nativeFastCheck = false;
    private boolean debugPrintCommandline = false;

    protected Jmccc() {
    }

    /**
     * Gets a new launcher instance.
     *
     * @return the launcher
     * @see LauncherBuilder
     * @deprecated Please use {@link LauncherBuilder} to create launchers. This
     * method may be removed in the future.
     */
    @Deprecated
    public static Launcher getLauncher() {
        return new Jmccc();
    }

    @Override
    public LaunchResult launch(LaunchOption option) throws LaunchException {
        return launch(option, null);
    }

    @Override
    public LaunchResult launch(LaunchOption option, GameProcessListener listener) throws LaunchException {
        return launch(generateLaunchArgs(option), listener);
    }

    /**
     * Gets whether to do a fast check on natives.
     *
     * @return true if jmccc was set to do a fast check on natives
     * @see #setNativeFastCheck(boolean)
     * @see LauncherBuilder#setNativeFastCheck(boolean)
     */
    public boolean isNativeFastCheck() {
        return nativeFastCheck;
    }

    /**
     * Sets whether to do a fast check on natives.
     *
     * @param nativeFastCheck true if the jmccc shall do a fast check on natives
     * @see #isNativeFastCheck()
     * @see LauncherBuilder#setNativeFastCheck(boolean)
     */
    public void setNativeFastCheck(boolean nativeFastCheck) {
        this.nativeFastCheck = nativeFastCheck;
    }

    /**
     * Gets whether to print the launch commandline for debugging.
     *
     * @return whether to print the launch commandline for debugging
     */
    public boolean isDebugPrintCommandline() {
        return debugPrintCommandline;
    }

    /**
     * Sets whether to print the launch commandline for debugging.
     *
     * @param debugPrintCommandline whether to print the launch commandline for debugging.
     */
    public void setDebugPrintCommandline(boolean debugPrintCommandline) {
        this.debugPrintCommandline = debugPrintCommandline;
    }

    private LaunchResult launch(LaunchArgument arg, GameProcessListener listener) throws LaunchException {
        String[] commandline = arg.generateCommandline();
        if (debugPrintCommandline) {
            printDebugCommandline(commandline);
        }

        ProcessBuilder processBuilder = new ProcessBuilder(commandline);
        processBuilder.directory(arg.getLaunchOption().getRuntimeDirectory().getRoot());

        Process process;
        try {
            process = processBuilder.start();
        } catch (SecurityException | IOException e) {
            throw new LaunchException("Failed to start process", e);
        }

        ProcessMonitor monitor;
        if (listener == null) {
            monitor = new DaemonStreamPumpMonitor(process);
        } else {
            monitor = new LoggingMonitor(process, listener);
        }
        monitor.start();

        return new LaunchResult(monitor, process);
    }

    private LaunchArgument generateLaunchArgs(LaunchOption option) throws LaunchException {
        Objects.requireNonNull(option);
        MinecraftDirectory mcdir = option.getMinecraftDirectory();
        Version version = option.getVersion();

        // check libraries
        Set<Library> missing = version.getMissingLibraries(mcdir);
        if (!missing.isEmpty()) {
            throw new MissingDependenciesException(missing.toString());
        }

        Set<File> javaLibraries = new HashSet<>();
        File nativesDir = mcdir.getNatives(version);
        for (Library library : version.getLibraries()) {
            File libraryFile = mcdir.getLibrary(library);
            if (library instanceof Native) {
                try {
                    decompressZipWithExcludes(libraryFile, nativesDir, ((Native) library).getExtractExcludes());
                } catch (IOException e) {
                    throw new LaunchException("Failed to uncompress " + libraryFile, e);
                }
            } else {
                javaLibraries.add(libraryFile);
            }
        }

        if (version.isLegacy()) {
            try {
                buildLegacyAssets(mcdir, version);
            } catch (IOException e) {
                throw new LaunchException("Failed to build virtual assets", e);
            }
        }

        AuthInfo auth = option.getAuthenticator().auth();

        Map<String, String> tokens = new HashMap<String, String>();
        String token = auth.getToken();
        String assetsDir = version.isLegacy() ? mcdir.getVirtualLegacyAssets().toString() : mcdir.getAssets().toString();
        tokens.put("assets_root", assetsDir);
        tokens.put("game_assets", assetsDir);
        tokens.put("auth_access_token", token);
        tokens.put("auth_session", token);
        tokens.put("auth_player_name", auth.getUsername());
        tokens.put("auth_uuid", auth.getUUID());
        tokens.put("user_type", auth.getUserType());
        tokens.put("user_properties", new JSONObject(auth.getProperties()).toString());
        tokens.put("version_name", version.getVersion());
        tokens.put("assets_index_name", version.getAssets());
        tokens.put("game_directory", option.getRuntimeDirectory().toString());

        String type = version.getType();
        if (type != null) {
            tokens.put("version_type", type);
        }

        return new LaunchArgument(option, tokens, javaLibraries, nativesDir);
    }

    private void buildLegacyAssets(MinecraftDirectory mcdir, Version version) throws IOException {
        Set<Asset> assets = Versions.resolveAssets(mcdir, version);
        if (assets != null)
            for (Asset asset : assets)
                FileUtils.copyFile(mcdir.getAsset(asset), mcdir.getVirtualAsset(asset));
    }

    private void decompressZipWithExcludes(File zip, File outputDir, Set<String> excludes) throws IOException {
        if (!outputDir.exists())
            outputDir.mkdirs();

        try (ZipInputStream in = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry entry;
            byte[] buf = null;

            while ((entry = in.getNextEntry()) != null) {
                boolean excluded = false; // true if the file is in excludes list
                if (excludes != null) {
                    for (String exclude : excludes) {
                        if (entry.getName().startsWith(exclude)) {
                            excluded = true;
                            break;
                        }
                    }
                }

                if (!excluded) {
                    // 1 unused byte for sentinel
                    if (buf == null || buf.length < entry.getSize() - 1) {
                        buf = new byte[(int) entry.getSize() + 1];
                    }
                    int len = 0;
                    int read;
                    // read the zipped data fully
                    while ((read = in.read(buf, len, buf.length - len)) != -1) {
                        if (read == 0) {
                            // reach the sentinel
                            throw new IOException("actual length and entry length mismatch");
                        }
                        len += read;
                    }

                    File outFile = new File(outputDir, entry.getName());
                    boolean match; // true if two files are the same
                    if (outFile.isFile() && outFile.length() == entry.getSize()) {
                        // same length, check the content
                        match = true;
                        if (!nativeFastCheck) {
                            try (InputStream targetin = new BufferedInputStream(new FileInputStream(outFile))) {
                                for (int i = 0; i < len; i++) {
                                    if (buf[i] != (byte) targetin.read()) {
                                        match = false;
                                        break;
                                    }
                                }
                            }
                        }
                    } else {
                        // different length
                        match = false;
                    }

                    if (!match) {
                        if (!outFile.getName().contains(".")) {
                            outFile.mkdirs();
                            continue;
                        }
                        try (OutputStream out = new FileOutputStream(outFile)) {
                            out.write(buf, 0, len);
                        }
                    }
                }

                in.closeEntry();
            }
        }

    }

    private void printDebugCommandline(String[] commandline) {
        StringBuilder sb = new StringBuilder();
        sb.append("jmccc:\n");
        for (String arg : commandline) {
            sb.append(arg).append('\n');
        }
        System.err.println(sb.toString());
    }

}
