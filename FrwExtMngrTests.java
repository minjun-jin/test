package com.jpmc.kcg.frw;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
class FrwExtMngrTests {

	@InjectMocks
	FrwExtMngr frwExtMngr;
	@Mock
	SocketFactory socketFactory;
	@Mock
	Socket socket;
	@Mock
	Vo vo;
	@Mock
	File file;

	@SneakyThrows
	@Test
	void test() {
		SystemProperties systemProperties = new SystemProperties();
		ReflectionTestUtils.setField(frwExtMngr, "systemProperties", systemProperties);
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			StringBuilder sb = new StringBuilder("0020HDRREQSTTS0928195144");
//			sb.setCharAt(9, 'S');
//			sb.append(StringUtils.leftPad(String.valueOf(1), 3, '0'));
//			sb.append(StringUtils.rightPad("TEST", 20));
//			sb.append(StringUtils.leftPad(String.valueOf(1), 1, '0'));
//			sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream(sb.toString(), "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			frwExtMngr.getSsnSttsMap();
//		}
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream("    HDRREQSTTS0928195144", "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			frwExtMngr.getSsnSttsMap();
//		} catch (Throwable t) {
//		}
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream("0000   REQSTTS0928195144", "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			frwExtMngr.getSsnSttsMap();
//		} catch (Throwable t) {
//		}
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			StringBuilder sb = new StringBuilder("0020HDRREQLLST0928195144");
//			sb.setCharAt(9, 'S');
//			sb.append(StringUtils.leftPad(String.valueOf(1), 3, '0'));
//			sb.append(StringUtils.rightPad("TEST", 88));
//			sb.append(StringUtils.leftPad(String.valueOf(1), 12, '0'));
//			sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream(sb.toString(), "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			frwExtMngr.getLogFileMap();
//		} catch (Throwable t) {
//		}
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			StringBuilder sb = new StringBuilder("0020HDRREQLLST0928195144");
//			sb.setCharAt(9, 'S');
//			sb.append(StringUtils.rightPad("TEST", 88));
//			sb.append(StringUtils.leftPad(String.valueOf(1), 12, '0'));
//			sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream(sb.toString(), "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
//				frwExtMngr.receiveLog("");
//			}
//		} catch (Throwable t) {
//		}
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFSND0928195144TEST    000000000001", "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
//				frwExtMngr.deleteExt("");
//			}
//		} catch (Throwable t) {
//		}
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFSND0928195144TEST    000000000001", "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
//				frwExtMngr.receiveExt("");
//			}
//		} catch (Throwable t) {
//		}
//		try (MockedConstruction<Socket> mockedConstruction = Mockito.mockConstruction(Socket.class, (mock, context) -> {
//			Mockito.when(mock.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFSND0928195144TEST    000000000001", "EUC-KR"));
//			Mockito.when(mock.getOutputStream()).thenReturn(new ByteArrayOutputStream());
//		})) {
//			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
//				mockedStatic.when(() -> FileUtils.getFile(Mockito.nullable(String.class), Mockito.nullable(String.class))).thenReturn(file);
//				Mockito.when(file.exists()).thenReturn(true);
//				frwExtMngr.sendExt("");
//			}
//		} catch (Throwable t) {
//		}
		try (MockedStatic<SSLSocketFactory> mockedStatic0 = Mockito.mockStatic(SSLSocketFactory.class)) {
			mockedStatic0.when(() -> SSLSocketFactory.getDefault()).thenReturn(socketFactory);
			frwExtMngr.afterPropertiesSet();
			Mockito.when(socketFactory.createSocket(Mockito.nullable(String.class), Mockito.anyInt())).thenReturn(socket);
			StringBuilder sb = new StringBuilder("0020HDRREQSTTS0928195144");
			sb.setCharAt(9, 'S');
			sb.append(StringUtils.leftPad(String.valueOf(1), 3, '0'));
			sb.append(StringUtils.rightPad("TEST", 20));
			sb.append(StringUtils.leftPad(String.valueOf(1), 1, '0'));
			sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream(sb.toString(), "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try {
				frwExtMngr.getSsnSttsMap();
			} catch (Throwable t) {
			}
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("    HDRREQSTTS0928195144", "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try {
				frwExtMngr.getSsnSttsMap();
			} catch (Throwable t) {
			}
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0000   REQSTTS0928195144", "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try {
				frwExtMngr.getSsnSttsMap();
			} catch (Throwable t) {
			}
			sb = new StringBuilder("0020HDRREQLLST0928195144");
			sb.setCharAt(9, 'S');
			sb.append(StringUtils.leftPad(String.valueOf(1), 3, '0'));
			sb.append(StringUtils.rightPad("TEST", 88));
			sb.append(StringUtils.leftPad(String.valueOf(1), 12, '0'));
			sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream(sb.toString(), "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try {
				frwExtMngr.getLogFileMap();
			} catch (Throwable t) {
			}
			sb = new StringBuilder("0020HDRREQLLST0928195144");
			sb.setCharAt(9, 'S');
			sb.append(StringUtils.rightPad("TEST", 88));
			sb.append(StringUtils.leftPad(String.valueOf(1), 12, '0'));
			sb.replace(0, 4, StringUtils.leftPad(String.valueOf(sb.length() - 4), 4, '0'));
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream(sb.toString(), "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
				frwExtMngr.receiveLog("");
			} catch (Throwable t) {
			}
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFSND0928195144TEST    000000000001", "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
				frwExtMngr.deleteExt("");
			} catch (Throwable t) {
			}
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFSND0928195144TEST    000000000001", "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
				frwExtMngr.receiveExt("");
			} catch (Throwable t) {
			}
			Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFSND0928195144TEST    000000000001", "EUC-KR"));
			Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
			try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
				mockedStatic.when(() -> FileUtils.getFile(Mockito.nullable(String.class), Mockito.nullable(String.class))).thenReturn(file);
				Mockito.when(file.exists()).thenReturn(true);
				frwExtMngr.sendExt("");
			} catch (Throwable t) {
			}
		}
		try (MockedStatic<FileUtils> mockedStatic1 = Mockito.mockStatic(FileUtils.class)) {
			try (MockedStatic<IOUtils> mockedStatic2 = Mockito.mockStatic(IOUtils.class)) {
				frwExtMngr.openOutputStream("");
			}
		}
		try (MockedStatic<FileUtils> mockedStatic1 = Mockito.mockStatic(FileUtils.class)) {
			try (MockedStatic<IOUtils> mockedStatic2 = Mockito.mockStatic(IOUtils.class)) {
				frwExtMngr.openInputStream("");
			}
		}
		try (MockedStatic<IOUtils> mockedStatic = Mockito.mockStatic(IOUtils.class)) {
			frwExtMngr.closeQuietly((Closeable) null);
			frwExtMngr.closeQuietly((Closeable) null, (Closeable) null);
		}
		try (MockedStatic<FileUtils> mockedStatic = Mockito.mockStatic(FileUtils.class)) {
			frwExtMngr.deleteQuietly("");
		}
		try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
			try (MockedStatic<IOUtils> mockedStatic = Mockito.mockStatic(IOUtils.class)) {
				frwExtMngr.write(byteArrayOutputStream, vo);
			}
		}
		try (InputStream inputStream = IOUtils.toInputStream("", "EUC-KR")) {
			try (MockedStatic<IOUtils> mockedStatic = Mockito.mockStatic(IOUtils.class)) {
				frwExtMngr.read(inputStream, 0);
			}
		}
		try (InputStream inputStream = IOUtils.toInputStream("", "EUC-KR")) {
			try (MockedStatic<VOUtils> mockedStatic = Mockito.mockStatic(VOUtils.class)) {
				frwExtMngr.read(inputStream, Vo.class);
			}
		}
		try (InputStream inputStream = IOUtils.toInputStream(" ", "EUC-KR")) {
			try (MockedStatic<VOUtils> mockedStatic = Mockito.mockStatic(VOUtils.class)) {
				frwExtMngr.read(inputStream, Vo.class);
			}
		}
	}

}
