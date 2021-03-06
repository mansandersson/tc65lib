package org.javacint.console;

//#if sdkns == "siemens"
import com.siemens.icm.io.file.*;
//#elif sdkns == "cinterion"
//# import com.cinterion.io.file.*;
//#endif
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Enumeration;
import java.util.Date;
import javax.microedition.io.Connector;
import javax.microedition.io.HttpConnection;
import org.javacint.common.BufferedReader;
import org.javacint.common.Strings;

/**
 * File navigation command receiver.
 *
 * This command receiver allows to navigate through directories and display
 * files.
 *
 */
public class FileNavigationCommandReceiver implements ConsoleCommand {

    private static final String root_ = "file:///a:";
    private static final int subIndex_ = root_.length();
    private FileConnection currentDir;

    public FileNavigationCommandReceiver() throws IOException {
        currentDir = (FileConnection) Connector.open(root_ + "/");
    }

    private String getShortPath(FileConnection file) {
        return file.getURL().
                substring(subIndex_);
    }

    private FileConnection getFile(String arg) throws IOException {
        if (arg.startsWith("/")) {
            return (FileConnection) Connector.open(root_ + arg);
        } else if (arg.equals("..")) {
            String url = currentDir.getURL().
                    substring(0, currentDir.getURL().
                    lastIndexOf('/'));

            if (url.equals("file:///a:")) {
                url = "file:///a:/";
            }

            return (FileConnection) Connector.open(url);
        } else {
            String url = currentDir.getURL();
            if (url.endsWith("/")) {
                url += arg;
            } else {
                url += "/" + arg;
            }
            return (FileConnection) Connector.open(url);
        }
    }

    private void commandCd(String arg, PrintStream out) throws IOException {
//		Vector out = new Vector();
        FileConnection dir;

        dir = getFile(arg);
        if (dir.exists() && dir.isDirectory()) {
            currentDir = dir;
            out.println("[CD] " + getShortPath(dir));
        } else {
            out.println("[CD ERR] Directory \"" + arg + "\" doesn't exist !");
        }
    }

    private void commandLs(PrintStream out) throws IOException {
        out.println("[LS] " + getShortPath(currentDir));

        for (Enumeration list = currentDir.list(); list.hasMoreElements();) {
            String fileName = (String) list.nextElement();
            FileConnection file = getFile(fileName);
            String line = (file.isDirectory() ? "D" : "F") + " " + getShortPath(file);
            out.print(line);
            for (int i = line.length(); i < 30; i++) {
                out.print(" ");
            }
            out.print(new Date(file.lastModified()));
            if (!file.isDirectory()) {
                out.print("\t" + file.fileSize() + "b");
            }
            out.println();
        }
    }

    private void copyStream(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[1024];

        while (is.available() > 0) {
            os.write(buffer, 0, is.read(buffer));
        }

        os.flush();
    }

    private void commandCat(String filename, PrintStream out) throws IOException {
        FileConnection file = getFile(filename);

        if (file.exists()) {
            out.println("[CAT] " + getShortPath(file));
            InputStream is = file.openInputStream();
            copyStream(is, out);
            out.println();
        } else {
            out.println("[CAT ERR] The file \"" + getShortPath(file) + "\" doesn't exist !");
        }
    }

    private void commandMore(String filename, InputStream in, PrintStream out) throws IOException {
        FileConnection file = getFile(filename);

        if (file.exists()) {
            out.println("[MORE] " + getShortPath(file));
            BufferedReader reader = new BufferedReader(file.openInputStream());
            try {
                int nb = 0;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (nb != 0 && nb++ % 40 == 0) {
                        out.println("--- MORE ---");
                        in.read();
                    }
                    out.println(line);
                }
            } finally {
                reader.close();
            }
        } else {
            out.println("[CAT ERR] The file \"" + getShortPath(file) + "\" doesn't exist !");
        }
    }

    private void commandDf(PrintStream out) {
        long usedSize = currentDir.usedSize();
        long totalSize = currentDir.totalSize();
        double perc = (double) ((usedSize * 10000) / totalSize) / 100;
        out.println("[DF] " + usedSize + "/" + totalSize + " - " + perc + "%");
    }

    private void commandPwd(PrintStream out) {
        out.println("[PWD] " + getShortPath(currentDir));
    }

    public boolean consoleCommand(String command, InputStream in, PrintStream out) {
        try {
            if (command.equals("ls")) {
                commandLs(out);
            } else if (command.startsWith("cd ")) {
                commandCd(command.substring(3), out);
            } else if (command.startsWith("cat ")) {
                commandCat(command.substring(4), out);
            } else if (command.startsWith("more ")) {
                commandMore(command.substring(5), in, out);
            } else if (command.equals("df")) {
                commandDf(out);
            } else if (command.equals("pwd")) {
                commandPwd(out);
            } else if (command.startsWith("mkdir ")) {
                commandMkDir(command.substring(6), out);
            } else if (command.startsWith("rm ")) {
                commandRm(command.substring(3), out);
            } else if (command.startsWith("wget ")) {
                commandWget(command.substring(5));
            } else if (command.startsWith("cp ")) {
                commandCp(command.substring(3), out);
            } else if (command.equals("help")) {
                out.println("[HELP] cd <dir>                         - Change current directory");
                out.println("[HELP] ls                               - List files in current directory");
                out.println("[HELP] cat <file>                       - Display the current file content");
                out.println("[HELP] more <file>                      - Pretty rendering of text file");
                out.println("[HELP] df                               - File system space usage");
                out.println("[HELP] rm <file>                        - Delete a file");
                out.println("[HELP] pwd                              - Display the current directory path");
                out.println("[HELP] wget <url> <file>                - Download an URL to a filename");
                return false;
            } else {
                return false;
            }
            return true;
        } catch (final IOException ex) {
            out.println("Ex: " + ex);
        }
        return false;
    }

    private void commandMkDir(String dirname, PrintStream out) throws IOException {
        FileConnection dir = getFile(dirname);
        dir.mkdir();
        out.println("[MKDIR] " + getShortPath(dir));

    }

    private void commandRm(String fileName, PrintStream out) throws IOException {
        FileConnection file = getFile(fileName);
        if (file.exists()) {
            file.delete();
            out.println("[RM] " + getShortPath(file));
        } else {
            out.println("[RM ERR] " + getShortPath(file) + " doesn't exist !");
        }
    }

    private void commandWget(String substring) throws IOException {
        String[] args = Strings.split(' ', substring);
        String url = args[0];
        String dest;
        if (args.length == 2) {
            dest = args[1];
        } else {
            dest = url.substring(url.lastIndexOf('/'));
        }
        if (dest.length() == 0) {
            dest = "out";
        }

        FileConnection output = getFile(dest);
        if (!output.exists()) {
            output.create();
        }
        OutputStream os = output.openOutputStream();
        HttpConnection input = (HttpConnection) Connector.open(url);
        InputStream is = input.openInputStream();

        copyStream(is, os);
        os.close();
        output.close();
        is.close();
        input.close();
    }

    private void commandCp(String substring, PrintStream out) throws IOException {
        String[] args = Strings.split(' ', substring);
        FileConnection src = getFile(args[0]);
        FileConnection dst = getFile(args[1]);
        if (src.exists() && !dst.exists()) {
            InputStream is = src.openInputStream();
            OutputStream os = dst.openOutputStream();
            try {
                copyStream(is, os);
            } finally {
                is.close();
                os.close();
                src.close();
                dst.close();
            }
        } else {
            out.println("[ CP ] Error: Source doesn't exists or destination exists !");
        }
    }
}
