package com.jpmc.kcg.ext;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ThreadUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(MockitoExtension.class)
class ExtFCmnTests {

	@Mock
	Properties properties;
	@Mock
	ExecutorService executorService;
//	@Mock
//	Map<String, Pair<String, String>> ssnSttsMap;
	@Mock
	ServerSocketFactory serverSocketFactory;
	@Mock
	ServerSocket serverSocket;
	@Mock
	Socket socket;

	@SneakyThrows
	@Test
	void test() {
		AtomicInteger atomicInteger = new AtomicInteger();
		Mockito.doAnswer(invocation -> {
			Runnable runnable = invocation.getArgument(0);
			runnable.run();
			return null;
		}).when(executorService).execute(Mockito.nullable(Runnable.class));
		Mockito.doAnswer(invocation -> {
			return 2 < atomicInteger.incrementAndGet();
		}).when(executorService).isShutdown();
//		Mockito.when(ssnSttsMap.get(Mockito.nullable(String.class))).thenReturn(new SimpleEntry<>("TEST", "0"));
		Map<String, Entry<String, String>> ssnSttsMap = new TreeMap<>();
		ssnSttsMap.put("FCMN_FRW_X", new SimpleEntry<>("QRCV_IFT_X", "0"));
		Mockito.when(serverSocketFactory.createServerSocket(Mockito.anyInt())).thenReturn(serverSocket);
		Mockito.when(serverSocket.accept()).thenReturn(socket);
		try (MockedStatic<ThreadUtils> mockedStatic0 = Mockito.mockStatic(ThreadUtils.class)) {
			try (MockedStatic<FileUtils> mockedStatic1 = Mockito.mockStatic(FileUtils.class)) {
				try (MockedStatic<EXUtils> mockedStatic2 = Mockito.mockStatic(EXUtils.class)) {
					try (MockedStatic<SSLServerSocketFactory> mockedStatic3 = Mockito.mockStatic(SSLServerSocketFactory.class)) {
						mockedStatic3.when(() -> SSLServerSocketFactory.getDefault()).thenReturn(serverSocketFactory);
//						mockedStatic2.when(() -> EXUtils.newServerSocket(Mockito.anyInt())).thenReturn(serverSocket);
						mockedStatic2.when(() -> EXUtils.write(Mockito.anyInt(), Mockito.nullable(String.class), Mockito.nullable(String.class))).then(invocation -> {
							return null;
						});
						ExtFCmn extFCmn = new ExtFCmn(properties,
							executorService,
							ssnSttsMap,
						"FCMN_FRW_X");
						Mockito.when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQPOLL0000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQSTOP0000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQKILL0000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQSTTS0000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQSEND0000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQRECV0000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQRECV0000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDR00000000000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("002000000000000000000000", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("                        ", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFDEL                              ", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFSND                              ", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0040HDRREQFRCV                              ", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0020HDRREQLLST          ", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0100HDRREQLRCV                                                                                          ", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
						Mockito.when(socket.getInputStream()).thenReturn(IOUtils.toInputStream("0100HDRREQRSLT                                                                                          ", "EUC-KR"));
						atomicInteger.set(0);
						extFCmn.run();
					}
				}
			}
		}
	}

}
