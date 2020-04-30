package com.wm.common.utils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;

public class ExcelUtils {
    private static final Logger logger = LoggerFactory.getLogger(ExcelUtils.class);

    private static final DataFormatter formatter = new DataFormatter();
    private static final Gson gson = new Gson();

    public static <T> List<T> toExcel(String filename, InputStream stream, Class<T> clazz, List<String> columnNames) {

        try {
            String extension = getFileExtension(filename);
            Workbook workbook;
            if (extension.equals("xls")) {
                workbook = new HSSFWorkbook(stream);
            } else if (extension.equals("xlsx")) {
                workbook = new XSSFWorkbook(stream);
            } else {
                throw new Exception("unknown extension: " + extension);
            }

            //只支持一个sheet页读取
            Sheet sheet = workbook.getSheetAt(0);
            //不含表头
            int maxRowIdx = sheet.getLastRowNum();

            JSONArray jsonArray = new JSONArray();
            for (int i = 1; i <= maxRowIdx; i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    continue;
                }
                if (isRowEmpty(row)) {
                    continue;
                }

                JSONObject jsonObject = new JSONObject();
                for (int j = 0; j < columnNames.size() && j <= row.getLastCellNum(); j++) {
                    String val = formatter.formatCellValue(row.getCell(j));
                    jsonObject.put(columnNames.get(j), val);
                }
                jsonArray.put(jsonObject);
            }
            return gson.fromJson(jsonArray.toString(), TypeToken.getParameterized(List.class, clazz).getType());
        } catch (Exception e) {
            logger.error("toExcel error: filename={}", filename, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 判断当前行是否为空
     *
     * @param row
     * @return
     */
    public static boolean isRowEmpty(Row row) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != Cell.CELL_TYPE_BLANK) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取扩展文件名
     *
     * @param fileName
     * @return
     */
    private static String getFileExtension(String fileName) {
        int extIdx = fileName.lastIndexOf(".");
        if (extIdx >= 1 && extIdx != fileName.length() - 1) {
            return fileName.substring(extIdx + 1);
        } else {
            throw new RuntimeException("error file name: " + fileName);
        }
    }
}
