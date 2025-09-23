package test;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Test {

	public static void addCellValue(Row row, CellStyle cellStyle, Object value) {
		Cell cell = row.createCell(row.getPhysicalNumberOfCells());
		cell.setCellStyle(cellStyle);
		if (null == value) {
			cell.setBlank();
			return;
		}
		if (value instanceof String v) {
			v = StringUtils.substringBefore(v, "[EN]");
			v = StringUtils.substringBefore(v, "[EN");
			v = StringUtils.remove(v, "[KR]");
			v = StringUtils.remove(v, "[KR");
			v = StringUtils.replace(v, "\r\n", "\n");
			v = StringUtils.stripEnd(v, "\n");
			if (StringUtils.startsWith(v, "절차\n")) {
				v = StringUtils.substring(v, 3);
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
/////////// 엑셀제한보정 ///////////////////////////////////////////////////////
			if (32767 < StringUtils.length(v)) {
				value = StringUtils.substring(v, 0, 32767);
			}
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
			cell.setCellValue(v);
			return;
		}
		if (value instanceof LocalDate v) {
			cell.setCellValue(v);
			return;
		}
		if (value instanceof LocalDateTime v) {
			cell.setCellValue(v);
			return;
		}
		if (value instanceof BigDecimal v) {
			cell.setCellValue(v.doubleValue());
			return;
		}
		cell.setCellValue(String.valueOf(value));
	}

	@SneakyThrows
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		List<String> cList = List.of(
//			"번호",
//			"테스트단계",
//			"테스트유형",
//			"업무대분류",
//			"업무중분류",
//			"테스트ID",
//			"테스트시나리오ID",
//			"테스트시나리오명",
//			"테스트케이스ID",
//			"테스트케이스명",
//			"테스트스텝ID",
//			"테스트스텝명",
//			"요구사항ID",
//			"요구사항명",
//			"화면경로",
//			"프로그램구분",
//			"프로그램ID",
//			"프로그램명",
//			"인터페이스",
//			"데이터검증",
//			"수행절차및설명",
//			"사전조건",
//			"입력항목",
//			"예상결과",
//			"개발자",
//			"PL",
//			"테스터",
//			"테스트종료예정일",
//			"테스트일자",
//			"테스트상태",
//			"고객IT",
//			"테스트일자고객IT",
//			"테스트상태고객IT",
//			"현업",
//			"테스트일자현업",
//			"테스트상태현업",
//			"의견",
//			"최종상태수정자",
//			"최종상태수정일"
			"테스트스텝ID",
			"테스트스텝명",
			"프로그램ID",
			"프로그램명",
			"Role",
			"수행절차및설명",
			"사전조건",
			"입력항목",
			"예상결과",
			"의견",
			"업무중분류",
			"테스트시나리오ID",
			"테스트시나리오명",
			"테스트케이스ID",
			"테스트케이스명"
		);
		Map<String, Map<String, List<Map<String, String>>>> fMap = new TreeMap<>();
	{
		try (Connection connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1/test", "entropy", "entropy")) {
//			try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM 테스트 WHERE 테스트단계 = '2차 E2E테스트' ORDER BY 테스트ID, 테스트케이스ID")) {
//			try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM 테스트 WHERE 테스트단계 = 'E2E테스트' ORDER BY 테스트ID, 테스트케이스ID")) {
			try (PreparedStatement preparedStatement = connection.prepareStatement("SELECT * FROM 테스트 WHERE 테스트단계 = '인수테스트' ORDER BY 테스트ID, 테스트케이스ID")) {
				try (ResultSet resultSet = preparedStatement.executeQuery()) {
					ResultSetMetaData resultSetMetaData = resultSet.getMetaData();
					int columnCount = resultSetMetaData.getColumnCount();
					List<Map<String, String>> columnMapList = new ArrayList<>();
					while (resultSet.next()) {
						String 테스트시나리오ID = StringUtils.left(resultSet.getString("테스트시나리오ID"), 6);
						String 업무중분류 = resultSet.getString("업무중분류");
						if (3 == StringUtils.length(업무중분류) &&
							!StringUtils.endsWith(테스트시나리오ID, 업무중분류)) {
							테스트시나리오ID = StringUtils.join(StringUtils.left(테스트시나리오ID, 3), 업무중분류);
						}
						Map<String, List<Map<String, String>>> sMap = fMap.get(테스트시나리오ID);
						if (null == sMap) {
							sMap = new TreeMap<>();
							fMap.put(테스트시나리오ID, sMap);
						}
						List<Map<String, String>> rList = sMap.get(resultSet.getString("테스트시나리오ID"));
						if (null == rList) {
							rList = new ArrayList<>();
							sMap.put(resultSet.getString("테스트시나리오ID"), rList);
						}
						Map<String, String> cMap = new TreeMap<>();
//						for (String c : cList) {
//							cMap.put(c, resultSet.getString(c));
//						}
						for (int i = 0; i < columnCount; i++) {
							cMap.put(resultSetMetaData.getColumnName(i + 1), resultSet.getString(i + 1));
						}
						rList.add(cMap);
					}
				}
			}
		}
	}
	{
		Entry<String, Map<String, List<Map<String, String>>>>[] fArray = fMap.entrySet().toArray(new Entry[0]);
		for (Entry<String, Map<String, List<Map<String, String>>>> fEntry : fArray) {
			Map<String, List<Map<String, String>>> sMap = fEntry.getValue();
			Entry<String, List<Map<String, String>>>[] sArray = sMap.entrySet().toArray(new Entry[0]);
			for (Entry<String, List<Map<String, String>>> sEntry : sArray) {
				List<Map<String, String>> rList = sEntry.getValue();
				Set<String> aSet = new TreeSet<>();
				Set<String> bSet = new TreeSet<>();
				for (Map<String, String> cMap : rList) {
					aSet.add(StringUtils.defaultString(cMap.get("테스트케이스ID")));
					bSet.add(cMap.get("테스트스텝ID"));
				}
				if (1 < aSet.size() &&
					1 < bSet.size()) {
					for (Map<String, String> cMap : rList) {
						List<Map<String, String>> wList = sMap.get(StringUtils.left(cMap.get("테스트ID"), 15));
						if (null == wList) {
							wList = new ArrayList<>();
							sMap.put(StringUtils.left(cMap.get("테스트ID"), 15), wList);
						}
						wList.add(cMap);
					}
					sMap.remove(sEntry.getKey());
				}
			}
		}
	}
	{
		Entry<String, Map<String, List<Map<String, String>>>>[] fArray = fMap.entrySet().toArray(new Entry[0]);
		for (Entry<String, Map<String, List<Map<String, String>>>> fEntry : fArray) {
			try (FileOutputStream fileOutputStream = FileUtils.openOutputStream(FileUtils.getFile(StringUtils.join("D:\\", fEntry.getKey(), ".xlsx")))) {
				try (Workbook workbook = new XSSFWorkbook()) {
					DataFormat dataFormat = workbook.createDataFormat();
					Font font = workbook.createFont();
					font.setFontName("GulimChe");
					font.setFontHeightInPoints((short) 9);
					CellStyle headStyle = workbook.createCellStyle();
					headStyle.setFont(font);
					headStyle.setAlignment(HorizontalAlignment.CENTER);
					headStyle.setVerticalAlignment(VerticalAlignment.CENTER);
					headStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
					headStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
					CellStyle textStyle = workbook.createCellStyle();
					textStyle.setFont(font);
					textStyle.setAlignment(HorizontalAlignment.LEFT);
					textStyle.setVerticalAlignment(VerticalAlignment.CENTER);
					textStyle.setWrapText(true);
					CellStyle dateStyle = workbook.createCellStyle();
					dateStyle.setFont(font);
					dateStyle.setAlignment(HorizontalAlignment.CENTER);
					dateStyle.setVerticalAlignment(VerticalAlignment.CENTER);
					dateStyle.setDataFormat(dataFormat.getFormat("yyyy-mm-dd"));
					CellStyle realStyle = workbook.createCellStyle();
					realStyle.setFont(font);
					realStyle.setAlignment(HorizontalAlignment.RIGHT);
					realStyle.setVerticalAlignment(VerticalAlignment.CENTER);
					realStyle.setDataFormat(dataFormat.getFormat("#,##0.00"));
					Map<String, List<Map<String, String>>> sMap = fEntry.getValue();
					Entry<String, List<Map<String, String>>>[] sArray = sMap.entrySet().toArray(new Entry[0]);
					for (Entry<String, List<Map<String, String>>> sEntry : sArray) {
						Sheet sheet = workbook.createSheet(sEntry.getKey());
						Row hdr = sheet.createRow(0);
						for (String c : cList) {
							if (org.apache.commons.codec.binary.StringUtils.equals(c, "의견")) {
								addCellValue(hdr, headStyle, "비고");
								continue;
							}
							addCellValue(hdr, headStyle, c);
						}
						List<Map<String, String>> rList = sEntry.getValue();
						for (Map<String, String> cMap : rList) {
							Row row = sheet.createRow(sheet.getPhysicalNumberOfRows());
							for (String c : cList) {
								addCellValue(row, textStyle, cMap.get(c));
							}
						}
						for (int i = 0; i < cList.size(); i++) {
							sheet.autoSizeColumn(i);
						}
					}
					workbook.write(fileOutputStream);
				}
			}
		}
	}
	}

}
