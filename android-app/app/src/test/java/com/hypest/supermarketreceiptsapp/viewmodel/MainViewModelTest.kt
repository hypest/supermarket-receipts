package com.hypest.supermarketreceiptsapp.viewmodel

import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository // Re-add import
import com.hypest.supermarketreceiptsapp.domain.repository.ReceiptRepository // Re-add import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private lateinit var mockReceiptRepository: ReceiptRepository // Re-add mock
    private lateinit var mockAuthRepository: AuthRepository // Re-add mock

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockReceiptRepository = mock() // Re-add mock creation
        mockAuthRepository = mock() // Re-add mock creation
        viewModel = MainViewModel(mockReceiptRepository, mockAuthRepository) // Re-add injection
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onPermissionResult updates hasCameraPermission state`() {
        // When granted
        viewModel.onPermissionResult(true)
        // Then
        assertTrue(viewModel.hasCameraPermission.value)
        assertTrue(viewModel.saveStatus.value is SaveStatus.Idle) // Re-add assertion

        // When denied
        viewModel.onPermissionResult(false)
        // Then
        assertFalse(viewModel.hasCameraPermission.value)
        assertTrue(viewModel.saveStatus.value is SaveStatus.Error) // Re-add assertion
        assertEquals("Camera permission denied.", (viewModel.saveStatus.value as SaveStatus.Error).message) // Re-add assertion
    }

    // Re-add tests related to saving URL
    @Test
    fun `onQrCodeScanned saves url successfully`() = runTest {
        // Given
        val url = "https://example.com/receipt"
        val userId = "user-123"
        whenever(mockAuthRepository.getCurrentUserId()).thenReturn(userId)
        whenever(mockReceiptRepository.saveReceiptUrl(any(), any())).thenReturn(Result.success(Unit))

        // When
        viewModel.onQrCodeScanned(url)
        advanceUntilIdle()

        // Then
        verify(mockAuthRepository).getCurrentUserId()
        verify(mockReceiptRepository).saveReceiptUrl(eq(url), eq(userId))
        assertEquals(url, viewModel.scannedUrl.value)
        assertTrue(viewModel.saveStatus.value is SaveStatus.Success)
    }

    @Test
    fun `onQrCodeScanned handles save failure`() = runTest {
        // Given
        val url = "https://example.com/receipt"
        val userId = "user-123"
        val exception = RuntimeException("Database error")
        whenever(mockAuthRepository.getCurrentUserId()).thenReturn(userId)
        whenever(mockReceiptRepository.saveReceiptUrl(any(), any())).thenReturn(Result.failure(exception))

        // When
        viewModel.onQrCodeScanned(url)
        advanceUntilIdle()

        // Then
        verify(mockAuthRepository).getCurrentUserId()
        verify(mockReceiptRepository).saveReceiptUrl(eq(url), eq(userId))
        assertEquals(url, viewModel.scannedUrl.value)
        assertTrue(viewModel.saveStatus.value is SaveStatus.Error)
        assertEquals(exception.message, (viewModel.saveStatus.value as SaveStatus.Error).message)
    }

    @Test
    fun `onQrCodeScanned handles null userId`() = runTest {
        // Given
        val url = "https://example.com/receipt"
        whenever(mockAuthRepository.getCurrentUserId()).thenReturn(null)

        // When
        viewModel.onQrCodeScanned(url)
        advanceUntilIdle()

        // Then
        verify(mockAuthRepository).getCurrentUserId()
        verify(mockReceiptRepository, never()).saveReceiptUrl(any(), any())
        assertEquals(url, viewModel.scannedUrl.value)
        assertTrue(viewModel.saveStatus.value is SaveStatus.Error)
        assertEquals("User not logged in.", (viewModel.saveStatus.value as SaveStatus.Error).message)
    }

     @Test
    fun `resetScanState resets state correctly`() = runTest {
        // Given: Set some non-idle state first
        val url = "https://example.com/receipt"
        val userId = "user-123"
        whenever(mockAuthRepository.getCurrentUserId()).thenReturn(userId)
        whenever(mockReceiptRepository.saveReceiptUrl(any(), any())).thenReturn(Result.success(Unit))
        viewModel.onQrCodeScanned(url)
        advanceUntilIdle()
        assertTrue(viewModel.saveStatus.value is SaveStatus.Success) // Verify initial state

        // When
        viewModel.resetScanState()

        // Then
        assertNull(viewModel.scannedUrl.value)
        assertTrue(viewModel.saveStatus.value is SaveStatus.Idle) // Re-add assertion
    }

    @Test
    fun `onQrCodeScanned updates scannedUrl state`() {
        // Given
        val url = "https://test.com"

        // When
        viewModel.onQrCodeScanned(url)

        // Then
        assertEquals(url, viewModel.scannedUrl.value)
    }
}
