package io.crest.extensions.datasource.provider;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * 优先加载扩展 JDBC 驱动类的 URL 类加载器
 */
public class ExtendedJdbcClassLoader extends URLClassLoader {

    /**
     * 当前类加载器关联的 JDBC 驱动完整类名
     */
    private String driver;
    /**
     * 返回已配置的 JDBC 驱动完整类名
     */
    public String getDriver() {
        return driver;
    }

    /**
     * 设置后续数据源注册使用的 JDBC 驱动完整类名
     */
    public void setDriver(String driver) {
        this.driver = driver;
    }


    /**
     * 使用驱动 URL 列表创建类加载器
     */
    public ExtendedJdbcClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * 优先从子加载器加载类，让随驱动携带的依赖可以覆盖父加载器类
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 先检查目标类是否已经被当前加载器加载
            Class<?> c = findLoadedClass(name);

            if (c != null) {
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
            try {
                c = findClass(name);
                if (c != null) {
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            } catch (ClassNotFoundException e) {
                // 忽略未找到异常，继续尝试父加载器
            }


            try {
                if (getParent() != null) {
                    c = super.loadClass(name, resolve);
                    if (c != null) {
                        if (resolve) {
                            resolveClass(c);
                        }
                        return c;
                    }
                }
            } catch (ClassNotFoundException e) {
                // 忽略未找到异常，继续尝试系统类加载器
            }
            try {
                c = findSystemClass(name);
                if (c != null) {
                    if (resolve) {
                        resolveClass(c);
                    }
                    return c;
                }
            } catch (ClassNotFoundException e) {
                // 忽略未找到异常，统一在末尾抛出目标类缺失
            }
            throw new ClassNotFoundException(name);
        }
    }



    /**
     * 将文件路径加入类加载器搜索 URL
     */
    public void addFile(String s) throws IOException {
        File f = new File(s);
        addFile(f);
    }

    /**
     * 将本地文件加入类加载器搜索 URL
     */
    public void addFile(File f) throws IOException {
        addFile(f.toURI().toURL());
    }

    /**
     * 将 URL 加入类加载器搜索路径
     */
    public void addFile(URL u) throws IOException {
        try {
            this.addURL(u);
        } catch (Throwable t) {
            io.crest.utils.LogUtil.error(t.getMessage(), t);
            throw new IOException("Error, could not add URL to system classloader");
        }
    }
}
