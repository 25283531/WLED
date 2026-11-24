# 10.0.1.172 烧录前备份与回滚记录（M0）

- 设备：`10.0.1.172`，MAC `e80690a6c20c`，ESP32-S3，16 MB Flash / 8 MB OPI PSRAM
- 烧录前固件：WLED `16.0.1`（`release=ESP32-S3_16MB_opi`，`brand=WLED`，`product=FOSS`，`fxcount=220`）
- 烧录前布局：`leds.count=345`、`matrix=15×23`（错误配置，仅作为回滚前状态记录）
- 备份时间：2026-11-24

## 备份文件

| 文件 | 说明 |
|---|---|
| `info.json` | 烧录前 `/json/info` 全量回读 |
| `cfg.json` | 烧录前 `/json/cfg`（含 led/um/wifi/def 等） |
| `state.json` | 烧录前 `/json/state` |
| `presets.json` | 烧录前 `/json/presets`（当前为空） |
| `rollback-WLED_16.0.1_ESP32-S3_16MB_opi.bin` | 官方 WLED 16.0.1 回滚固件（1,232,640 B） |
| `SHA256SUMS.txt` | 新固件与回滚固件的 SHA256 |

校验和：

- 新固件 `ColorLamp-S3-C01-ota.bin`：`028D230F0F1DD07859011B68AA56B986A884AF6F28D9D82F76B2D9AAD8CECDCD`
- 回滚固件：`BD31BBA8E4CB16C387BCF5DFD78BE11924A3DCDA54FD1207FCC648EB97306E9B`

## 回滚步骤（优先 OTA）

1. 确认设备可访问：`GET http://10.0.1.172/json/info`。
2. 上传回滚固件（字段名必须是 `update`）：

   ```powershell
   curl.exe -s -F "update=@g:\workbuddy\WLED\2026-11-24\app-dev-taskbook\work\backup\e80690a6c20c\rollback-WLED_16.0.1_ESP32-S3_16MB_opi.bin" http://10.0.1.172/update
   ```

3. 等待设备重启（约 10–20 秒），回读 `/json/info` 确认 `ver=16.0.1`、`product=FOSS`。
4. 恢复配置（如固件重置了 cfg）：

   ```powershell
   curl.exe -s -X POST -H "Content-Type: application/json" --data-binary "@g:\workbuddy\WLED\2026-11-24\app-dev-taskbook\work\backup\e80690a6c20c\cfg.json" http://10.0.1.172/json/cfg
   ```

5. 恢复预设（若备份非空）：通过 `POST /upload` 上传 `presets.json` 到 FS 根目录，或在 Web 的 Security & Updates 页恢复。

## USB 救砖（OTA 回滚失败时）

使用 CI 产物中的 bootloader/partitions 与官方回滚固件：

```powershell
esptool.py --chip esp32s3 -p COMx -b 460800 write_flash `
  0x0     esp32lichuang-bootloader.bin `
  0x8000  esp32lichuang-partitions.bin `
  0x10000 rollback-WLED_16.0.1_ESP32-S3_16MB_opi.bin
```

> 注意：USB 烧录会覆盖分区表；若之后要回到 M0 固件，重新用 `ColorLamp-S3-C01-ota.bin` OTA。
> 备份文件只保存在本地 `work/backup/`，不提交仓库。
