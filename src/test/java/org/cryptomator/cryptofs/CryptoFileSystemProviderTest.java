package org.cryptomator.cryptofs;

import static java.nio.file.Paths.get;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.util.Arrays.asList;
import static org.cryptomator.cryptofs.CryptoFileSystemProperties.cryptoFileSystemProperties;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.ByteChannel;
import java.nio.channels.FileChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(Theories.class)
public class CryptoFileSystemProviderTest {

	@Rule
	public MockitoRule mockitoRule = MockitoJUnit.rule();

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	private CryptoFileSystems fileSystems = mock(CryptoFileSystems.class);

	private CryptoPath cryptoPath = mock(CryptoPath.class);
	private CryptoPath secondCryptoPath = mock(CryptoPath.class);
	private CryptoFileSystem cryptoFileSystem = mock(CryptoFileSystem.class);

	private Path otherPath = mock(Path.class);
	private FileSystem otherFileSystem = mock(FileSystem.class);
	private FileSystemProvider otherProvider = mock(FileSystemProvider.class);

	@SuppressWarnings("deprecation")
	private CryptoFileSystemProvider inTest = new CryptoFileSystemProvider(fileSystems);

	@DataPoints
	@SuppressWarnings("unchecked")
	public static final List<InvocationWhichShouldFailWithProviderMismatch> INVOCATIONS_FAILING_WITH_PROVIDER_MISMATCH = asList( //
			shouldFailWithProviderMismatch("newAsynchronousFileChannel", (inTest, otherPath) -> inTest.newAsynchronousFileChannel(otherPath, new HashSet<>(), mock(ExecutorService.class))), //
			shouldFailWithProviderMismatch("newFileChannel", (inTest, otherPath) -> inTest.newFileChannel(otherPath, new HashSet<>())), //
			shouldFailWithProviderMismatch("newByteChannel", (inTest, otherPath) -> inTest.newByteChannel(otherPath, new HashSet<>())), //
			shouldFailWithProviderMismatch("newDirectoryStream", (inTest, otherPath) -> inTest.newDirectoryStream(otherPath, mock(Filter.class))), //
			shouldFailWithProviderMismatch("createDirectory", (inTest, otherPath) -> inTest.createDirectory(otherPath)), //
			shouldFailWithProviderMismatch("delete", (inTest, otherPath) -> inTest.delete(otherPath)), //
			shouldFailWithProviderMismatch("copy", (inTest, otherPath) -> inTest.copy(otherPath, otherPath)), //
			shouldFailWithProviderMismatch("move", (inTest, otherPath) -> inTest.move(otherPath, otherPath)), //
			shouldFailWithProviderMismatch("isHidden", (inTest, otherPath) -> inTest.isHidden(otherPath)), //
			shouldFailWithProviderMismatch("getFileStore", (inTest, otherPath) -> inTest.getFileStore(otherPath)), //
			shouldFailWithProviderMismatch("checkAccess", (inTest, otherPath) -> inTest.checkAccess(otherPath)), //
			shouldFailWithProviderMismatch("getFileAttributeView", (inTest, otherPath) -> inTest.getFileAttributeView(otherPath, FileAttributeView.class)), //
			shouldFailWithProviderMismatch("readAttributesWithClass", (inTest, otherPath) -> inTest.readAttributes(otherPath, BasicFileAttributes.class)), //
			shouldFailWithProviderMismatch("readAttributesWithString", (inTest, otherPath) -> inTest.readAttributes(otherPath, "fooBar")), //
			shouldFailWithProviderMismatch("setAttribute", (inTest, otherPath) -> inTest.setAttribute(otherPath, "a", "b")) //
	);

	@Before
	public void setup() {
		when(cryptoPath.getFileSystem()).thenReturn(cryptoFileSystem);
		when(secondCryptoPath.getFileSystem()).thenReturn(cryptoFileSystem);
		when(cryptoFileSystem.provider()).thenReturn(inTest);

		when(otherPath.getFileSystem()).thenReturn(otherFileSystem);
		when(otherFileSystem.provider()).thenReturn(otherProvider);
	}

	@Theory
	public void testInvocationsWithPathFromOtherProviderFailWithProviderMismatchException(InvocationWhichShouldFailWithProviderMismatch shouldFailWithProviderMismatch) throws IOException {
		thrown.expect(ProviderMismatchException.class);

		shouldFailWithProviderMismatch.invoke(inTest, otherPath);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testFileSystemsIsMock() {
		assertThat(inTest.getCryptoFileSystems(), is(fileSystems));
	}

	@Test
	public void testGetSchemeReturnsCryptomatorScheme() {
		assertThat(inTest.getScheme(), is("cryptomator"));
	}

	@Test
	public void testNewFileSystemInvokesFileSystemsCreate() throws IOException {
		Path pathToVault = get("a").toAbsolutePath();
		URI uri = CryptoFileSystemUris.createUri(pathToVault);
		CryptoFileSystemProperties properties = cryptoFileSystemProperties().withPassphrase("asd").build();
		when(fileSystems.create(eq(pathToVault), eq(properties))).thenReturn(cryptoFileSystem);

		FileSystem result = inTest.newFileSystem(uri, properties);

		assertThat(result, is(cryptoFileSystem));
	}

	@Test
	public void testGetFileSystemInvokesFileSystemsGetWithPathToVaultFromUri() {
		Path pathToVault = get("a").toAbsolutePath();
		URI uri = CryptoFileSystemUris.createUri(pathToVault);
		when(fileSystems.get(pathToVault)).thenReturn(cryptoFileSystem);

		FileSystem result = inTest.getFileSystem(uri);

		assertThat(result, is(cryptoFileSystem));
	}

	@Test
	public void testGetPathDelegatesToFileSystem() {
		Path pathToVault = get("a").toAbsolutePath();
		URI uri = CryptoFileSystemUris.createUri(pathToVault, "c", "d");
		when(fileSystems.get(pathToVault)).thenReturn(cryptoFileSystem);
		when(cryptoFileSystem.getPath("/c/d")).thenReturn(cryptoPath);

		Path result = inTest.getPath(uri);

		assertThat(result, is(cryptoPath));
	}

	@Test
	public void testNewAsyncFileChannelFailsIfOptionsContainAppend() throws IOException {
		Path irrelevantPath = null;
		ExecutorService irrelevantExecutor = null;

		thrown.expect(IllegalArgumentException.class);

		inTest.newAsynchronousFileChannel(irrelevantPath, new HashSet<>(asList(APPEND)), irrelevantExecutor);
	}

	@Test
	@SuppressWarnings("deprecation")
	public void testNewAsyncFileChannelReturnsAsyncDelegatingFileChannelWithNewFileChannelAndExecutor() throws IOException {
		@SuppressWarnings("unchecked")
		Set<OpenOption> options = mock(Set.class);
		ExecutorService executor = mock(ExecutorService.class);
		FileChannel channel = mock(FileChannel.class);
		when(cryptoFileSystem.newFileChannel(cryptoPath, options)).thenReturn(channel);

		AsynchronousFileChannel result = inTest.newAsynchronousFileChannel(cryptoPath, options, executor);

		assertThat(result, is(instanceOf(AsyncDelegatingFileChannel.class)));
		AsyncDelegatingFileChannel asyncDelegatingFileChannel = (AsyncDelegatingFileChannel) result;
		assertThat(asyncDelegatingFileChannel.getChannel(), is(channel));
		assertThat(asyncDelegatingFileChannel.getExecutor(), is(executor));
	}

	@Test
	public void testNewFileChannelDelegatesToFileSystem() throws IOException {
		@SuppressWarnings("unchecked")
		Set<OpenOption> options = mock(Set.class);
		FileChannel channel = mock(FileChannel.class);
		when(cryptoFileSystem.newFileChannel(cryptoPath, options)).thenReturn(channel);

		FileChannel result = inTest.newFileChannel(cryptoPath, options);

		assertThat(result, is(channel));
	}

	@Test
	public void testNewByteChannelDelegatesToFileSystem() throws IOException {
		@SuppressWarnings("unchecked")
		Set<OpenOption> options = mock(Set.class);
		FileChannel channel = mock(FileChannel.class);
		when(cryptoFileSystem.newFileChannel(cryptoPath, options)).thenReturn(channel);

		ByteChannel result = inTest.newByteChannel(cryptoPath, options);

		assertThat(result, is(channel));
	}

	@Test
	public void testNewDirectoryStreamDelegatesToFileSystem() throws IOException {
		@SuppressWarnings("unchecked")
		DirectoryStream<Path> stream = mock(DirectoryStream.class);
		@SuppressWarnings("unchecked")
		Filter<Path> filter = mock(Filter.class);
		when(cryptoFileSystem.newDirectoryStream(cryptoPath, filter)).thenReturn(stream);

		DirectoryStream<Path> result = inTest.newDirectoryStream(cryptoPath, filter);

		assertThat(result, is(stream));
	}

	@Test
	public void testCreateDirectoryDelegatesToFileSystem() throws IOException {
		@SuppressWarnings("unchecked")
		FileAttribute<String> attr = mock(FileAttribute.class);

		inTest.createDirectory(cryptoPath, attr);

		verify(cryptoFileSystem).createDirectory(cryptoPath, attr);
	}

	@Test
	public void testDeleteDelegatesToFileSystem() throws IOException {
		inTest.delete(cryptoPath);

		verify(cryptoFileSystem).delete(cryptoPath);
	}

	@Test
	public void testCopyDelegatesToFileSystem() throws IOException {
		CopyOption option = mock(CopyOption.class);

		inTest.copy(cryptoPath, secondCryptoPath, option);

		verify(cryptoFileSystem).copy(cryptoPath, secondCryptoPath, option);
	}

	@Test
	public void testMoveDelegatesToFileSystem() throws IOException {
		CopyOption option = mock(CopyOption.class);

		inTest.move(cryptoPath, secondCryptoPath, option);

		verify(cryptoFileSystem).move(cryptoPath, secondCryptoPath, option);
	}

	@Test
	public void testIsSameFileReturnsFalseIfFileSystemsOfPathsDoNotMatch() throws IOException {
		assertFalse(inTest.isSameFile(cryptoPath, otherPath));
	}

	@Test
	public void testIsSameFileReturnsFalseIfRealPathsOfTwoPathsAreNotEqual() throws IOException {
		when(cryptoPath.toRealPath()).thenReturn(cryptoPath);
		when(secondCryptoPath.toRealPath()).thenReturn(secondCryptoPath);

		assertFalse(inTest.isSameFile(cryptoPath, secondCryptoPath));
	}

	@Test
	public void testIsSameFileReturnsTureIfRealPathsOfTwoPathsAreEqual() throws IOException {
		when(cryptoPath.toRealPath()).thenReturn(cryptoPath);
		when(secondCryptoPath.toRealPath()).thenReturn(cryptoPath);

		assertTrue(inTest.isSameFile(cryptoPath, secondCryptoPath));
	}

	@Test
	public void testIsHiddenDelegatesToFileSystemIfTrue() throws IOException {
		when(cryptoFileSystem.isHidden(cryptoPath)).thenReturn(true);

		assertTrue(inTest.isHidden(cryptoPath));
	}

	@Test
	public void testIsHiddenDelegatesToFileSystemIfFalse() throws IOException {
		when(cryptoFileSystem.isHidden(cryptoPath)).thenReturn(false);

		assertFalse(inTest.isHidden(cryptoPath));
	}

	@Test
	public void testCheckAccessDelegatesToFileSystem() throws IOException {
		AccessMode mode = AccessMode.EXECUTE;

		inTest.checkAccess(cryptoPath, mode);

		verify(cryptoFileSystem).checkAccess(cryptoPath, mode);
	}

	@Test
	public void testGetFileStoreDelegatesToFileSystem() throws IOException {
		CryptoFileStore fileStore = mock(CryptoFileStore.class);
		when(cryptoFileSystem.getFileStore()).thenReturn(fileStore);

		FileStore result = inTest.getFileStore(cryptoPath);

		assertThat(result, is(fileStore));
	}

	@Test
	public void testGetFileAttributeViewDelegatesToFileSystem() {
		FileAttributeView view = mock(FileAttributeView.class);
		LinkOption option = LinkOption.NOFOLLOW_LINKS;
		when(cryptoFileSystem.getFileAttributeView(cryptoPath, FileAttributeView.class, option)).thenReturn(view);

		FileAttributeView result = inTest.getFileAttributeView(cryptoPath, FileAttributeView.class, option);

		assertThat(result, is(view));
	}

	@Test
	public void testReadAttributesWithTypeDelegatesToFileSystem() throws IOException {
		BasicFileAttributes attributes = mock(BasicFileAttributes.class);
		LinkOption option = LinkOption.NOFOLLOW_LINKS;
		when(cryptoFileSystem.readAttributes(cryptoPath, BasicFileAttributes.class, option)).thenReturn(attributes);

		BasicFileAttributes result = inTest.readAttributes(cryptoPath, BasicFileAttributes.class, option);

		assertThat(result, is(attributes));
	}

	@Test
	public void testReadAttributesWithNameDelegatesToFileSystem() throws IOException {
		@SuppressWarnings("unchecked")
		Map<String, Object> attributes = mock(Map.class);
		LinkOption option = LinkOption.NOFOLLOW_LINKS;
		String name = "foobar";
		when(cryptoFileSystem.readAttributes(cryptoPath, name, option)).thenReturn(attributes);

		Map<String, Object> result = inTest.readAttributes(cryptoPath, name, option);

		assertThat(result, is(attributes));
	}

	@Test
	public void testSetAttributeDelegatesToFileSystem() throws IOException {
		LinkOption option = LinkOption.NOFOLLOW_LINKS;
		String attribute = "foo";
		String value = "bar";

		inTest.setAttribute(cryptoPath, attribute, value, option);

		verify(cryptoFileSystem).setAttribute(cryptoPath, attribute, value, option);
	}

	private static InvocationWhichShouldFailWithProviderMismatch shouldFailWithProviderMismatch(String name, Invocation invocation) {
		return new InvocationWhichShouldFailWithProviderMismatch(name, invocation);
	}

	private static class InvocationWhichShouldFailWithProviderMismatch {

		private final String name;
		private final Invocation invocation;

		public InvocationWhichShouldFailWithProviderMismatch(String name, Invocation invocation) {
			this.name = name;
			this.invocation = invocation;
		}

		public void invoke(CryptoFileSystemProvider inTest, Path otherPath) throws IOException {
			invocation.invoke(inTest, otherPath);
		}

		@Override
		public String toString() {
			return name;
		}

	}

	@FunctionalInterface
	private interface Invocation {

		void invoke(CryptoFileSystemProvider inTest, Path otherPath) throws IOException;

	}

}
