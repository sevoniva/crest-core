package io.crest.datasource.server;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.crest.api.ds.DatasourceDriverApi;
import io.crest.api.ds.vo.DriveDTO;
import io.crest.api.ds.vo.DriveJarDTO;
import io.crest.datasource.dao.auto.entity.CoreDriver;
import io.crest.datasource.dao.auto.entity.CoreDriverJar;
import io.crest.datasource.dao.auto.mapper.CoreDriverJarMapper;
import io.crest.datasource.dao.auto.mapper.CoreDriverMapper;
import io.crest.extensions.datasource.dto.DatasourceDTO;
import io.crest.storage.StorageService;
import io.crest.utils.BeanUtils;
import io.crest.utils.FileUtils;
import io.crest.utils.Md5Utils;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Transactional(rollbackFor = Exception.class)
@RestController
@RequestMapping("/datasource-driver")
/**
 * 数据源驱动接口控制器，负责驱动配置和驱动包管理
 */
public class DatasourceDriverServer implements DatasourceDriverApi {

    @Value("${crest.path.custom-drivers:/opt/crest/custom-drivers/}")
    private String DRIVER_PATH;

    @Resource
    private CoreDriverMapper coreDriverMapper;

    @Resource
    private CoreDriverJarMapper coreDriverJarMapper;
    @Resource
    private StorageService storageService;

    /**
     * 按关键字查询数据源驱动
     */
    @Override
    public List<DatasourceDTO> query(String keyWord) {
        return null;
    }

    /**
     * 查询全部驱动配置
     */
    @Override
    public List<DriveDTO> list() {
        List<DriveDTO> driveDTOS = new ArrayList<>();
        List<CoreDriver> coreDrivers = coreDriverMapper.selectList(null);
        coreDrivers.forEach(coreDriver -> {
            DriveDTO datasourceDrive = new DriveDTO();
            BeanUtils.copyBean(datasourceDrive, coreDriver);
            datasourceDrive.setTypeDesc(""); // 驱动描述未在该接口中补充，保持空值由调用方兜底展示。
        });
        return driveDTOS;
    }

    /**
     * 按数据源类型查询驱动配置
     */
    @Override
    public List<DriveDTO> listByDsType(String dsType) {
        List<DriveDTO> driveDTOS = new ArrayList<>();
        QueryWrapper<CoreDriver> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("type", dsType);
        List<CoreDriver> coreDrivers = coreDriverMapper.selectList(queryWrapper);
        coreDrivers.forEach(coreDriver -> {
            DriveDTO datasourceDrive = new DriveDTO();
            BeanUtils.copyBean(datasourceDrive, coreDriver);
        });
        return driveDTOS;
    }

    /**
     * 新增驱动配置
     */
    @Override
    public DriveDTO save(DriveDTO datasourceDrive) {
        CoreDriver coreDriver = new CoreDriver();
        BeanUtils.copyBean(coreDriver, datasourceDrive);
        coreDriverMapper.insert(coreDriver);
        return datasourceDrive;
    }

    /**
     * 更新驱动配置
     */
    @Override
    public DriveDTO update(DriveDTO datasourceDrive) {
        CoreDriver coreDriver = new CoreDriver();
        BeanUtils.copyBean(coreDriver, datasourceDrive);
        coreDriverMapper.updateById(coreDriver);
        return datasourceDrive;
    }

    /**
     * 删除驱动配置及其驱动包记录
     */
    @Override
    public void delete(String driverId) {
        coreDriverMapper.deleteById(driverId);
        Map<String, Object> map = new HashMap<>();
        map.put("driverId", driverId);
        coreDriverJarMapper.deleteByMap(map);
    }


    /**
     * 查询指定驱动下的驱动包列表
     */
    @Override
    public List<DriveJarDTO> listDriverJar(String driverId) {
        List<DriveJarDTO> driveJarDTOS = new ArrayList<>();
        QueryWrapper<CoreDriverJar> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("driverId", driverId);
        coreDriverJarMapper.selectList(queryWrapper).forEach(coreDriverJar -> {
            DriveJarDTO driveJarDTO = new DriveJarDTO();
            BeanUtils.copyBean(driveJarDTO, coreDriverJar);
            driveJarDTOS.add(driveJarDTO);
        });
        return driveJarDTOS;
    }

    /**
     * 删除指定驱动包文件和数据库记录
     */
    @Override
    public void deleteDriverJar(String jarId) {
        CoreDriverJar driverJar = coreDriverJarMapper.selectById(jarId);
        coreDriverJarMapper.deleteById(jarId);
        storageService.deleteFile(DRIVER_PATH, driverJar.getDriverId(), driverJar.getTransName());
        // 删除驱动文件后，后续连接会重新按现有驱动文件加载。
    }

    /**
     * 上传驱动 Jar 包并写入驱动包记录
     */
    @Override
    public DriveJarDTO uploadJar(@RequestParam("driverId") String driverId, @RequestParam("jarFile") MultipartFile jarFile) throws Exception {
        CoreDriver coreDriver = coreDriverMapper.selectById(driverId);
        if (coreDriver == null) {
            throw new RuntimeException("DRIVER_NOT_FOUND");
        }
        String filename = jarFile.getOriginalFilename();
        if (filename == null || !filename.endsWith(".jar")) {
            throw new RuntimeException("NOT_JAR");
        }
        FileUtils.validateUploadFilename(filename);

        QueryWrapper<CoreDriverJar> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("fileName", filename);
        if (!CollectionUtils.isEmpty(coreDriverJarMapper.selectList(queryWrapper))) {
            throw new Exception("A file with the same name already exists：" + filename);
        }

        String transName = Md5Utils.md5(filename) + ".jar";
        saveJarFile(jarFile, driverId, transName);

        CoreDriverJar coreDriverJar = new CoreDriverJar();
        coreDriverJar.setDriverId(driverId);
        coreDriverJar.setVersion("");
        coreDriverJar.setFileName(filename);
        coreDriverJar.setDriverClass(String.join(",", new ArrayList<>()));
        coreDriverJar.setIsTransName(true);
        coreDriverJar.setTransName(transName);
        coreDriverJarMapper.insert(coreDriverJar);
        // 上传后的驱动信息立即入库，后续连接按最新驱动文件加载。

        DriveJarDTO driveJarDTO = new DriveJarDTO();
        BeanUtils.copyBean(driveJarDTO, coreDriverJar);
        return driveJarDTO;
    }

    /**
     * 将上传的驱动 Jar 安全保存到驱动目录
     */
    private void saveJarFile(MultipartFile file, String driverId, String transName) throws Exception {
        File f = storageService.resolve(DRIVER_PATH, driverId, transName);
        // 自定义驱动统一落到 StorageService，生产环境由 RWX PVC 供所有 Pod 加载。
        try (OutputStream fileOutputStream = storageService.newOutputStream(f)) {
            fileOutputStream.write(file.getBytes());
            fileOutputStream.flush();
        }
    }
}
