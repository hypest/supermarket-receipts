package com.hypest.supermarketreceiptsapp.viewmodel

import com.hypest.supermarketreceiptsapp.domain.repository.AuthRepository
import io.github.jan.supabase.auth.status.SessionStatus // Use v3 package
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@ExperimentalCoroutinesApi
class AuthViewModelTest {

    // Use TestCoroutineDispatcher for controlling coroutine execution in tests
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: AuthViewModel
    private lateinit var mockAuthRepository: AuthRepository

    // Mock flow for session status
    private val sessionStatusFlow = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Set main dispatcher for ViewModelScope
        mockAuthRepository = mock {
            on { sessionStatus } doReturn sessionStatusFlow // Provide the mock flow
        }
        viewModel = AuthViewModel(mockAuthRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // Reset main dispatcher after test
    }

    @Test
    fun `signOut calls repository signOut`() = runTest {
        // When
        viewModel.signOut()
        advanceUntilIdle() // Ensure coroutine launched in ViewModel completes

        // Then
        verify(mockAuthRepository).signOut()
    }

    @Test
    fun `signInWithEmail calls repository signInWithEmail`() = runTest {
        // Given
        val email = "test@example.com"
        val password = "password123"
        // Mock the repository call to return success immediately
        whenever(mockAuthRepository.signInWithEmail(any(), any())).thenReturn(Result.success(Unit))

        // When
        viewModel.signInWithEmail(email, password)
        advanceUntilIdle() // Ensure coroutine completes

        // Then
        verify(mockAuthRepository).signInWithEmail(eq(email), eq(password))
        // TODO: Add assertions for loading/error states if implemented in ViewModel
    }

    @Test
    fun `sessionStatus flow reflects repository flow`() = runTest {
        // Given
        val initialState = viewModel.sessionStatus.value
        assert(initialState is SessionStatus.Initializing) // Check initial state

        // When
        val expectedStatus = SessionStatus.NotAuthenticated(false)
        sessionStatusFlow.value = expectedStatus // Emit new status from mock repository
        advanceUntilIdle() // Allow flow collection to update

        // Then
        val newState = viewModel.sessionStatus.value
        assert(newState == expectedStatus)
    }

     @Test
    fun `signInWithEmail handles repository failure`() = runTest {
        // Given
        val email = "test@example.com"
        val password = "password123"
        val exception = RuntimeException("Login failed")
        whenever(mockAuthRepository.signInWithEmail(any(), any())).thenReturn(Result.failure(exception))

        // When
        viewModel.signInWithEmail(email, password)
        advanceUntilIdle()

        // Then
        verify(mockAuthRepository).signInWithEmail(eq(email), eq(password))
        // TODO: Assert that error state in ViewModel is updated (when implemented)
        // e.g., assertEquals(exception.message, viewModel.errorMessage.value)
    }
}
