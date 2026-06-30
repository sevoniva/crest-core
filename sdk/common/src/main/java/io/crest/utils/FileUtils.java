package io.crest.utils;

import io.crest.exception.CrestException;
import io.crest.i18n.Translator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件路径校验、上传、复制和读取工具
 */
public class FileUtils {

    /**
     * 校验上传文件名，阻止路径穿越、空字节和控制字符
     */
    public static void validateUploadFilename(String filename) {
        if (StringUtils.isBlank(filename)
                || filename.indexOf('/') >= 0
                || filename.indexOf('\\') >= 0
                || filename.contains("..")
                || filename.indexOf('\0') >= 0
                || containsControlCharacter(filename)) {
            CrestException.throwException(Translator.get("i18n_invalid_file_name"));
        }
    }

    /**
     * 确保目录存在并返回规范化目录对象
     */
    public static File ensureDirectory(String directoryPath) throws IOException {
        if (StringUtils.isBlank(directoryPath) || directoryPath.indexOf('\0') >= 0) {
            CrestException.throwException(Translator.get("i18n_invalid_file_name"));
        }
        File directory = new File(directoryPath).getCanonicalFile();
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create directory: " + directory.getPath());
        }
        if (!directory.isDirectory()) {
            throw new IOException("Path is not a directory: " + directory.getPath());
        }
        return directory;
    }

    /**
     * 在指定目录下解析文件名，并确保结果不会逃逸目录边界
     */
    public static File resolveUnderDirectory(String directoryPath, String filename) throws IOException {
        validateUploadFilename(filename);
        File directory = ensureDirectory(directoryPath);
        File target = new File(directory, filename).getCanonicalFile();
        if (!target.toPath().startsWith(directory.toPath())) {
            CrestException.throwException(Translator.get("i18n_invalid_file_name"));
        }
        return target;
    }

    /**
     * 判断文件名是否包含控制字符
     */
    private static boolean containsControlCharacter(String filename) {
        for (int i = 0; i < filename.length(); i++) {
            if (Character.isISOControl(filename.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建不存在的目录路径
     */
    public static void createIfAbsent(@NonNull Path path) throws IOException {
        Assert.notNull(path, "Path must not be null");

        if (Files.notExists(path)) {
            // 创建目录
            Files.createDirectories(path);
            LogUtil.debug("Created directory: [{}]", path);
        }
    }


    /**
     * Java文件操作 获取不带扩展名的文件名
     */
    public static String getFileNameNoEx(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length()))) {
                return filename.substring(0, dot);
            }
        }
        return filename;
    }

    /**
     * 获取文件扩展名，不带 .
     */
    public static String getExtensionName(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int dot = filename.lastIndexOf('.');
            if ((dot > -1) && (dot < (filename.length() - 1))) {
                return filename.substring(dot + 1);
            }
        }
        return filename;
    }

    /**
     * 确保指定路径存在，不存在时创建目录
     */
    public static void validateExist(String path) {
        File dir = new File(path);
        if (dir.exists()) return;
        dir.mkdirs();
    }

    /**
     * 将文件名解析成文件的上传路径
     */
    @SuppressWarnings("java/path-injection")
    public static File upload(MultipartFile file, String filePath) {
        String originalFilename = file.getOriginalFilename();
        validateUploadFilename(originalFilename);
        String name = getFileNameNoEx(originalFilename);
        String suffix = getExtensionName(originalFilename);
        try {
            String fileName = name + "." + suffix;
            File dest = resolveUnderDirectory(filePath, fileName);

            try (FileOutputStream fileOutputStream = new FileOutputStream(dest)) {
                fileOutputStream.write(file.getBytes());
                fileOutputStream.flush();
            }
            return dest;
        } catch (Exception e) {
            LogUtil.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 递归复制文件夹到目标路径
     */
    public static void copyFolder(String sourcePath, String targetPath) throws Exception {
        // 源文件夹路径
        File sourceFile = new File(sourcePath);
        // 目标文件夹路径
        File targetFile = new File(targetPath);

        if (!sourceFile.exists()) {
            throw new Exception("文件夹不存在");
        }
        if (!sourceFile.isDirectory()) {
            throw new Exception("源文件夹不是目录");
        }
        if (!targetFile.exists()) {
            targetFile.mkdirs();
        }
        if (!targetFile.isDirectory()) {
            throw new Exception("目标文件夹不是目录");
        }

        File[] files = sourceFile.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            // 文件要复制到的路径
            String movePath = targetFile + File.separator + file.getName();
            if (file.isDirectory()) {
                // 目录节点递归复制
                copyFolder(file.getAbsolutePath(), movePath);
            } else {
                // 文件节点直接复制内容
                BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
                BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(movePath));

                byte[] b = new byte[1024];
                int temp = 0;
                while ((temp = in.read(b)) != -1) {
                    out.write(b, 0, temp);
                }
                out.close();
                in.close();
            }
        }
    }


    /**
     * 复制单个文件到目标目录并返回目标路径
     */
    public static String copy(File source, String targetDir) throws IOException {
        String name = source.getName();
        String destPath = null;
        if (targetDir.endsWith("/") || targetDir.endsWith("\\")) {
            destPath = targetDir + name;
        } else {
            destPath = targetDir + "/" + name;
        }
        File DestFile = new File(destPath);
        if (!DestFile.getParentFile().exists()) {
            DestFile.getParentFile().mkdirs();
        }
        copyFileUsingFileChannels(source, DestFile);
        return destPath;
    }

    /**
     * 使用 NIO 通道复制文件内容
     */
    private static void copyFileUsingFileChannels(File source, File dest) throws IOException {
        FileChannel inputChannel = null;
        FileChannel outputChannel = null;
        try {
            inputChannel = new FileInputStream(source).getChannel();
            outputChannel = new FileOutputStream(dest).getChannel();
            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
        } finally {
            inputChannel.close();
            outputChannel.close();
        }
    }

    /**
     * 读取 JSON 文件内容
     */
    @SuppressWarnings("java/path-injection")
    public static String readJson(File file) {
        try (InputStream inputStream = new FileInputStream(file.getCanonicalFile());
             Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            int ch;
            StringBuilder sb = new StringBuilder();
            while ((ch = reader.read()) != -1) {
                sb.append((char) ch);
            }
            return sb.toString();
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除文件或递归删除目录
     */
    @SuppressWarnings("java/path-injection")
    public static void deleteFile(String path) {
        File file;
        try {
            file = new File(path).getCanonicalFile();
        } catch (IOException e) {
            CrestException.throwException("文件不存在");
            return;
        }
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    Arrays.stream(children).forEach(item -> deleteFile(item.getAbsolutePath()));
                }
            }
            file.delete();
        }
    }

    /**
     * 判断路径是否存在
     */
    public static boolean exist(String path) {
        File file = new File(path);
        return file.exists();
    }

    /**
     * 列出目录下的文件名
     */
    public static List<String> listFileNames(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return null;
        } else {
            File[] files = file.listFiles();

            assert files != null;

            return Arrays.stream(files).map(File::getName).collect(Collectors.toList());
        }
    }

    /**
     * 获取文件后缀名
     */
    public static String getSuffix(String fileName) {
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /**
     * 获取不含后缀的文件名前缀
     */
    public static String getPrefix(String fileName) {
        return fileName.substring(0, fileName.lastIndexOf("."));
    }

    /**
     * 读取文件字节内容
     */
    @SuppressWarnings("java/path-injection")
    public static byte[] readBytes(String path) {
        File file;
        try {
            file = new File(path).getCanonicalFile();
        } catch (IOException e) {
            CrestException.throwException("文件不存在");
            return null;
        }
        if (!file.exists() || !file.isFile()) {
            CrestException.throwException("文件不存在");
        }

        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }
            return bos.toByteArray();
        } catch (Exception e) {
            io.crest.utils.LogUtil.error(e.getMessage(), e);
            return null;
        }
    }


    /**
     * 递归删除目录及其内容
     */
    @SuppressWarnings("java/path-injection")
    public static boolean deleteDirectoryRecursively(String directoryPath) {
        File directory;
        try {
            directory = new File(directoryPath).getCanonicalFile();
        } catch (IOException e) {
            return false;
        }
        if (!directory.exists()) {
            return true;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectoryRecursively(file.getAbsolutePath());
                } else {
                    boolean deletionSuccess = file.delete();
                }
            }
        }
        return directory.delete();
    }
}
