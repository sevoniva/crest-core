package io.crest.storage;

import io.crest.exception.CrestException;
import io.crest.utils.FileUtils;
import io.crest.utils.LogUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 基于本地文件系统的存储实现，生产环境通过 RWX PVC 挂载共享目录。
 */
@Service
public class LocalStorageService implements StorageService {

    @Override
    public File resolve(String rootPath, String... pathSegments) {
        File root = rootDirectory(rootPath);
        File target = root;
        if (pathSegments != null) {
            for (String segment : pathSegments) {
                target = new File(target, validatePathSegment(segment));
            }
        }
        try {
            File canonicalTarget = target.getCanonicalFile();
            if (!canonicalTarget.toPath().startsWith(root.toPath())) {
                CrestException.throwException("文件路径越界");
            }
            return new ResolvedStorageFile(canonicalTarget);
        } catch (IOException e) {
            CrestException.throwException("文件路径解析失败");
            return target;
        }
    }

    @Override
    public File ensureDirectory(String rootPath, String... pathSegments) {
        File directory = resolve(rootPath, pathSegments);
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                CrestException.throwException("目录路径被文件占用: " + directory.getAbsolutePath());
            }
            return directory;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            CrestException.throwException("目录创建失败: " + directory.getAbsolutePath());
        }
        return directory;
    }

    @Override
    @SuppressWarnings("java/path-injection")
    public OutputStream newOutputStream(File file) throws IOException {
        return org.apache.commons.io.FileUtils.openOutputStream(requireResolvedFile(file));
    }

    @Override
    @SuppressWarnings("java/path-injection")
    public InputStream newInputStream(File file) throws IOException {
        return org.apache.commons.io.FileUtils.openInputStream(requireResolvedFile(file));
    }

    @Override
    public boolean isRegularFile(File file) {
        try {
            File canonicalFile = requireResolvedFile(file);
            return org.apache.commons.io.FileUtils.isRegularFile(canonicalFile);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public long size(File file) {
        try {
            return org.apache.commons.io.FileUtils.sizeOf(requireResolvedFile(file));
        } catch (IOException e) {
            LogUtil.warn("读取文件大小失败: " + e.getMessage());
            return 0L;
        }
    }

    @Override
    public boolean deleteDirectory(String rootPath, String... pathSegments) {
        if (pathSegments == null || pathSegments.length == 0) {
            CrestException.throwException("不允许删除存储根目录");
        }
        File directory = resolve(rootPath, pathSegments);
        return FileUtils.deleteDirectoryRecursively(directory.getAbsolutePath());
    }

    @Override
    public boolean deleteFile(String rootPath, String... pathSegments) {
        if (pathSegments == null || pathSegments.length == 0) {
            CrestException.throwException("不允许删除存储根目录");
        }
        File file = resolve(rootPath, pathSegments);
        try {
            org.apache.commons.io.file.PathUtils.deleteFile(file.toPath());
            return true;
        } catch (java.nio.file.NoSuchFileException e) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String validatePathSegment(String segment) {
        if (StringUtils.isBlank(segment)
                || segment.indexOf('\0') >= 0
                || segment.indexOf('/') >= 0
                || segment.indexOf('\\') >= 0
                || ".".equals(segment)
                || "..".equals(segment)) {
            CrestException.throwException("文件路径非法");
        }
        return segment;
    }

    private File rootDirectory(String rootPath) {
        if (StringUtils.isBlank(rootPath) || rootPath.indexOf('\0') >= 0) {
            CrestException.throwException("存储根目录非法");
        }
        try {
            return new File(rootPath).getCanonicalFile();
        } catch (IOException e) {
            CrestException.throwException("存储根目录解析失败");
            return new File(rootPath);
        }
    }

    private File requireResolvedFile(File file) throws IOException {
        if (file instanceof ResolvedStorageFile resolvedStorageFile) {
            return resolvedStorageFile.canonicalFile();
        }
        throw new IOException("File must be resolved by StorageService.");
    }

    private static final class ResolvedStorageFile extends File {
        private static final long serialVersionUID = 1L;

        private final File canonicalFile;

        private ResolvedStorageFile(File canonicalFile) {
            super(canonicalFile.getPath());
            this.canonicalFile = canonicalFile;
        }

        private File canonicalFile() {
            return canonicalFile;
        }
    }
}
