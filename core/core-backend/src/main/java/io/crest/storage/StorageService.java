package io.crest.storage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 业务文件存储抽象，屏蔽本地路径和后续存储实现差异。
 */
public interface StorageService {

    /**
     * 在指定根目录下解析安全路径，结果不得逃逸根目录。
     */
    File resolve(String rootPath, String... pathSegments);

    /**
     * 确保指定目录存在，并返回规范化目录。
     */
    File ensureDirectory(String rootPath, String... pathSegments);

    /**
     * 打开文件写入流，必要时创建父目录。
     */
    OutputStream newOutputStream(File file) throws IOException;

    /**
     * 打开文件读取流。
     */
    InputStream newInputStream(File file) throws IOException;

    /**
     * 判断目标是否是可读取的普通文件。
     */
    boolean isRegularFile(File file);

    /**
     * 读取文件大小。
     */
    long size(File file);

    /**
     * 删除指定目录及其内容。
     */
    boolean deleteDirectory(String rootPath, String... pathSegments);

    /**
     * 删除指定普通文件。
     */
    boolean deleteFile(String rootPath, String... pathSegments);
}
