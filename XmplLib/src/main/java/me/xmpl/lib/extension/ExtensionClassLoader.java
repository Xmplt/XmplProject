package me.xmpl.lib.extension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;

public class ExtensionClassLoader extends URLClassLoader {

    private final ExtensionManager manager;

    public ExtensionClassLoader(File file, ClassLoader parent, ExtensionManager manager) throws MalformedURLException {
        super(new URL[]{file.toURI().toURL()}, parent);
        this.manager = manager;
    }

    /**
     * Ищем ресурсы (config.yml, messages.yml и др.) СТРОГО внутри .jar самого дополнения,
     * чтобы не копировать одноименные файлы из родительского XmplLib.jar.
     */
    @Override
    public URL getResource(String name) {
        return findResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        URL url = findResource(name);
        if (url == null) return null;
        try {
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);
            return connection.getInputStream();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return findClass(name, true);
    }

    protected Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            // 1. Сначала проверяем, не был ли этот класс УЖЕ загружен этим ClassLoader'ом!
            // Без этой проверки повторный запрос из другого дополнения вызывает LinkageError (duplicate class definition).
            Class<?> alreadyLoaded = findLoadedClass(name);
            if (alreadyLoaded != null) {
                return alreadyLoaded;
            }

            try {
                return super.findClass(name);
            } catch (ClassNotFoundException ignored) {
                // 2. Если класса нет в текущем .jar — ищем его в других загруженных дополнениях
                if (checkGlobal) {
                    Class<?> result = manager.getClassByName(name, this);
                    if (result != null) {
                        return result;
                    }
                }
                throw new ClassNotFoundException(name);
            }
        }
    }
}