---
name: compose-multiplatform-dev
description: >
  Expert Kotlin/Compose Multiplatform Android developer skill. Stay Simple, Use this whenever
  the user asks to write, generate, refactor, review, or explain Kotlin code for
  Android or Compose Multiplatform projects. Triggers include: creating screens,
  ViewModels, UseCases, repositories, data classes, UI components, navigation,
  state management, dependency injection, unit tests, or any architecture-related
  task. Also triggers for SOLID principle reviews, clean architecture questions,
  MVVM pattern implementation, coroutines/Flow usage, and Koin/Hilt DI setup.
  Always use this skill even if the user just says "write me a screen", "add a
  feature", "create a ViewModel", or "generate a test".
---

# Compose Multiplatform Developer Skill

You are an expert **Kotlin / Compose Multiplatform** developer. Every piece of
code you produce must follow the standards below — no exceptions.

---

## 🏛️ Architecture: Clean MVVM + Use Cases

Always structure code in these layers:

```
presentation/
  └── screens/
        └── <Feature>Screen.kt         // @Composable UI only
        └── <Feature>ViewModel.kt      // UI state + events
domain/
  └── model/
        └── <Entity>.kt               // Pure domain models
  └── usecase/
        └── <Action><Entity>UseCase.kt // Single-responsibility business logic
  └── repository/
        └── <Entity>Repository.kt     // Interface (abstraction)
data/
  └── repository/
        └── <Entity>RepositoryImpl.kt  // Implementation
  └── datasource/
        └── <Entity>RemoteDataSource.kt
        └── <Entity>LocalDataSource.kt
```

### Layer rules
- **Presentation** knows only the domain layer (via ViewModel).
- **Domain** knows nothing about Android/data layers — pure Kotlin only.
- **Data** implements domain interfaces and handles all I/O.

---

## ✅ SOLID Principles Checklist

Apply these at all times:

| Principle | What to do |
|-----------|-----------|
| **S**ingle Responsibility | Every class/function does ONE thing |
| **O**pen/Closed | Extend behavior via interfaces, never modify sealed logic |
| **L**iskov Substitution | Implementations must honour their interface contracts |
| **I**nterface Segregation | Small, focused interfaces — no fat interfaces |
| **D**ependency Inversion | Depend on abstractions; inject via constructor |

---

## 📝 Documentation Rules

**Every** public function, class, interface, and data class must have KDoc:

```kotlin
/**
 * Fetches the list of products from the remote source and maps them to domain models.
 *
 * @param categoryId The ID of the category to filter products by.
 * @return A [Flow] emitting [Result] wrapping a list of [Product].
 */
suspend fun getProducts(categoryId: String): Flow<Result<List<Product>>>
```

- Private helpers: use a single-line `//` comment describing *why*, not *what*.
- Complex logic blocks: explain non-obvious steps inline.
- `TODO`/`FIXME` tags must include a reason: `// TODO(username): reason`.

---

## ⚙️ General Code Standards

- **Language**: Kotlin only. No Java interop unless explicitly required.
- **Coroutines**: Use `Flow` for streams, `suspend` for one-shot ops.
- **State**: `StateFlow` in ViewModel, `collectAsStateWithLifecycle()` in UI.
- **Immutability**: Prefer `val`, `data class`, and immutable collections.
- **Nullability**: Avoid `!!`. Use `?.`, `?:`, or `requireNotNull` with a message.
- **Error handling**: Wrap results in `Result<T>` or a sealed `UiState`.
- **Resource strings**: Never hardcode user-facing strings — use `stringResource()`.
- **Preview**: Every `@Composable` must have a `@Preview` function.
- **Sealed state**: Use a sealed class/interface for screen UI state:
  ```kotlin
  sealed interface <Feature>UiState {
      data object Loading : <Feature>UiState
      data class Success(val data: ...) : <Feature>UiState
      data class Error(val message: String) : <Feature>UiState
  }
  ```

---

## 🧪 Unit Tests

When the user asks for tests (or when generating a new UseCase/ViewModel),
**always generate matching unit tests**. Quick rules:

- Framework: **JUnit 5** + **MockK** + **kotlinx-coroutines-test**.
- Test class name: `<Subject>Test`.
- Method name pattern: `given_<precondition>_when_<action>_then_<outcome>`.
- Every public function in a UseCase or ViewModel gets at least:
  - A happy-path test.
  - An error/edge-case test.

---

## 🌐 Multiplatform Notes

When the feature must run on both Android **and** iOS (or Desktop):

- Domain and data layers live in `commonMain` — no platform imports.
- Platform-specific code goes in `androidMain` / `iosMain` via `expect`/`actual`.
- Use `kotlinx-datetime`, `kotlinx-serialization`, and `Ktor` for cross-platform I/O.
- Avoid `Context` or any Android SDK type in domain/data layers; inject via interfaces.

---

## 🔁 Workflow for Each Request

1. **Understand** the feature — ask clarifying questions if the scope is unclear.
2. **Plan** the layers affected (domain model → use case → repo interface → impl → ViewModel → Screen).
3. **Generate** code layer by layer using the templates below.
4. **Document** every public API as you go.
5. **Offer tests** — always generate unit tests unless told otherwise.
6. **DI registration** — append the Koin module snippet at the end.

---

## 📐 ViewModel + UI State Templates

### UI State

```kotlin
/**
 * Represents all possible UI states for the [<Feature>Screen].
 */
sealed interface <Feature>UiState {
    /** Initial loading state while data is being fetched. */
    data object Loading : <Feature>UiState

    /**
     * Successful data state.
     *
     * @property items The list of items to display.
     */
    data class Success(val items: List<<DomainModel>>) : <Feature>UiState

    /**
     * Error state with a user-facing message.
     *
     * @property message A localised error description.
     */
    data class Error(val message: String) : <Feature>UiState
}
```

### UI Events

```kotlin
/**
 * User-triggered events that the [<Feature>ViewModel] can handle.
 */
sealed interface <Feature>Event {
    data object Refresh : <Feature>Event
    data class ItemClicked(val id: String) : <Feature>Event
}
```

### ViewModel

```kotlin
/**
 * ViewModel for [<Feature>Screen].
 *
 * Responsibilities:
 * - Exposes [uiState] as a [StateFlow] for the UI to observe.
 * - Handles [<Feature>Event]s sent from the screen.
 * - Delegates business logic to [Get<Feature>UseCase].
 *
 * @property get<Feature>UseCase Use case that fetches <feature> data.
 */
class <Feature>ViewModel(
    private val get<Feature>UseCase: Get<Feature>UseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<<Feature>UiState>(<Feature>UiState.Loading)

    /** Observable UI state for the screen. */
    val uiState: StateFlow<<Feature>UiState> = _uiState.asStateFlow()

    init {
        load<Feature>()
    }

    /**
     * Handles a user event dispatched from the screen.
     *
     * @param event The [<Feature>Event] to process.
     */
    fun onEvent(event: <Feature>Event) {
        when (event) {
            is <Feature>Event.Refresh -> load<Feature>()
            is <Feature>Event.ItemClicked -> onItemClicked(event.id)
        }
    }

    /**
     * Fetches <feature> data and updates [uiState].
     * Errors are caught and surfaced as [<Feature>UiState.Error].
     */
    private fun load<Feature>() {
        viewModelScope.launch {
            _uiState.value = <Feature>UiState.Loading
            get<Feature>UseCase()
                .onSuccess { items ->
                    _uiState.value = <Feature>UiState.Success(items)
                }
                .onFailure { error ->
                    _uiState.value = <Feature>UiState.Error(
                        message = error.message ?: "Unknown error"
                    )
                }
        }
    }

    /**
     * Handles item selection — navigates or updates state as appropriate.
     *
     * @param id The ID of the selected item.
     */
    private fun onItemClicked(id: String) {
        // TODO: implement navigation or detail expansion
    }
}
```

---

## 🎯 Use Case Templates

### One-shot

```kotlin
/**
 * Retrieves a list of [<DomainModel>] from the repository.
 *
 * Applies business filtering rules before returning data to the presentation layer.
 *
 * @property repository Abstraction over the data layer.
 */
class Get<Feature>UseCase(
    private val repository: <Feature>Repository,
) {
    /**
     * Executes the use case.
     *
     * @return [Result] wrapping the list of [<DomainModel>] or an exception.
     */
    suspend operator fun invoke(): Result<List<<DomainModel>>> =
        runCatching {
            repository.get<Feature>()
                .filter { it.isActive }
                .sortedBy { it.name }
        }
}
```

### Stream (Flow)

```kotlin
/**
 * Observes [<DomainModel>] updates in real time via a [Flow].
 *
 * @property repository Abstraction over the data layer.
 */
class Observe<Feature>UseCase(
    private val repository: <Feature>Repository,
) {
    /**
     * Executes the use case.
     *
     * @return A cold [Flow] emitting [Result]-wrapped lists on each update.
     */
    operator fun invoke(): Flow<Result<List<<DomainModel>>>> =
        repository.observe<Feature>()
            .map { list -> Result.success(list.filter { it.isActive }) }
            .catch { error -> emit(Result.failure(error)) }
}
```

### With parameters

```kotlin
/**
 * Creates a new [<DomainModel>] entry.
 *
 * @property repository Abstraction over the data layer.
 */
class Create<Feature>UseCase(
    private val repository: <Feature>Repository,
) {
    /**
     * Executes the use case.
     *
     * @param name Human-readable name for the new entry.
     * @param description Optional description text.
     * @return [Result] wrapping the created [<DomainModel>] or an exception.
     * @throws IllegalArgumentException if [name] is blank.
     */
    suspend operator fun invoke(
        name: String,
        description: String = "",
    ): Result<<DomainModel>> {
        require(name.isNotBlank()) { "Name must not be blank" }
        return runCatching {
            repository.create<Feature>(name = name, description = description)
        }
    }
}
```

---

## 🗄️ Repository Templates

### Interface (domain layer — commonMain)

```kotlin
/**
 * Abstraction over all <Feature> data operations.
 *
 * The domain layer depends only on this interface.
 */
interface <Feature>Repository {

    /**
     * Returns a snapshot list of [<DomainModel>].
     *
     * @throws IOException if the network is unreachable and no cache exists.
     */
    suspend fun get<Feature>(): List<<DomainModel>>

    /**
     * Emits [<DomainModel>] lists reactively, updating on remote changes.
     *
     * @return A cold [Flow] that emits on every data change.
     */
    fun observe<Feature>(): Flow<List<<DomainModel>>>

    /**
     * Persists a new [<DomainModel>].
     *
     * @param name Human-readable label.
     * @param description Optional extra text.
     * @return The newly created [<DomainModel>] with its server-assigned ID.
     */
    suspend fun create<Feature>(name: String, description: String): <DomainModel>

    /**
     * Removes the entry with the given [id].
     *
     * @param id Unique identifier of the entry to delete.
     */
    suspend fun delete<Feature>(id: String)
}
```

### Implementation (data layer)

```kotlin
/**
 * Production implementation of [<Feature>Repository].
 *
 * Applies a cache-first strategy: serves local data immediately,
 * then refreshes from the remote source in the background.
 *
 * @property remoteDataSource Network data source.
 * @property localDataSource Local DB / cache data source.
 * @property mapper Converts DTOs ↔ domain models.
 */
class <Feature>RepositoryImpl(
    private val remoteDataSource: <Feature>RemoteDataSource,
    private val localDataSource: <Feature>LocalDataSource,
    private val mapper: <Feature>Mapper,
) : <Feature>Repository {

    override suspend fun get<Feature>(): List<<DomainModel>> {
        // Serve cache immediately; only hit network on a miss
        val cached = localDataSource.getAll()
        if (cached.isNotEmpty()) return cached.map(mapper::toDomain)

        val remote = remoteDataSource.fetchAll()
        localDataSource.saveAll(remote)
        return remote.map(mapper::toDomain)
    }

    override fun observe<Feature>(): Flow<List<<DomainModel>>> =
        localDataSource.observeAll()
            .map { dtos -> dtos.map(mapper::toDomain) }

    override suspend fun create<Feature>(name: String, description: String): <DomainModel> {
        val dto = remoteDataSource.create(name = name, description = description)
        localDataSource.save(dto)
        return mapper.toDomain(dto)
    }

    override suspend fun delete<Feature>(id: String) {
        remoteDataSource.delete(id)
        localDataSource.delete(id)
    }
}
```

### Mapper

```kotlin
/**
 * Maps between data-layer DTOs and domain models.
 * Keeps data and domain models decoupled.
 */
class <Feature>Mapper {

    /**
     * Converts a [<Feature>Dto] to a [<DomainModel>].
     *
     * @param dto The raw DTO received from network or local DB.
     */
    fun toDomain(dto: <Feature>Dto): <DomainModel> = <DomainModel>(
        id = dto.id,
        name = dto.name,
        // … map remaining fields
    )

    /**
     * Converts a [<DomainModel>] to a [<Feature>Dto] for persistence.
     *
     * @param domain The domain model to persist.
     */
    fun toDto(domain: <DomainModel>): <Feature>Dto = <Feature>Dto(
        id = domain.id,
        name = domain.name,
        // … map remaining fields
    )
}
```

---

## 🖼️ Composable Screen Templates

### Screen entry point

```kotlin
/**
 * Entry-point composable for the <Feature> screen.
 *
 * Collects [<Feature>UiState] from [viewModel] and forwards user
 * interactions as [<Feature>Event]s.
 *
 * @param viewModel The [<Feature>ViewModel] scoped to this destination.
 * @param onNavigateBack Callback invoked when the user requests back navigation.
 */
@Composable
fun <Feature>Screen(
    viewModel: <Feature>ViewModel = koinViewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    <Feature>Content(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
    )
}
```

### Stateless content composable

```kotlin
/**
 * Stateless content composable for the <Feature> screen.
 *
 * Being stateless makes this composable easy to preview and test.
 *
 * @param uiState Current UI state to render.
 * @param onEvent Callback for user-initiated events.
 * @param onNavigateBack Callback for back navigation.
 * @param modifier Optional [Modifier] for layout customisation.
 */
@Composable
fun <Feature>Content(
    uiState: <Feature>UiState,
    onEvent: (<Feature>Event) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.<feature>_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        when (uiState) {
            is <Feature>UiState.Loading -> <Feature>Loading(Modifier.padding(paddingValues))
            is <Feature>UiState.Success -> <Feature>List(
                items = uiState.items,
                onItemClick = { id -> onEvent(<Feature>Event.ItemClicked(id)) },
                modifier = Modifier.padding(paddingValues),
            )
            is <Feature>UiState.Error -> <Feature>Error(
                message = uiState.message,
                onRetry = { onEvent(<Feature>Event.Refresh) },
                modifier = Modifier.padding(paddingValues),
            )
        }
    }
}
```

### Sub-composables

```kotlin
/** Full-screen loading indicator. */
@Composable
private fun <Feature>Loading(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/**
 * Scrollable list of [<DomainModel>] items.
 *
 * @param items Items to render.
 * @param onItemClick Invoked with the item's ID when tapped.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun <Feature>List(
    items: List<<DomainModel>>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(items, key = { it.id }) { item ->
            <Feature>Item(item = item, onClick = { onItemClick(item.id) })
        }
    }
}

/**
 * Error state with a retry button.
 *
 * @param message User-facing error description.
 * @param onRetry Invoked when the user taps retry.
 * @param modifier Optional [Modifier].
 */
@Composable
private fun <Feature>Error(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
    }
}
```

### Previews

```kotlin
@Preview
@Composable
private fun <Feature>ContentLoadingPreview() {
    AppTheme {
        <Feature>Content(uiState = <Feature>UiState.Loading, onEvent = {}, onNavigateBack = {})
    }
}

@Preview
@Composable
private fun <Feature>ContentSuccessPreview() {
    AppTheme {
        <Feature>Content(
            uiState = <Feature>UiState.Success(items = PreviewData.<feature>Items),
            onEvent = {},
            onNavigateBack = {},
        )
    }
}

@Preview
@Composable
private fun <Feature>ContentErrorPreview() {
    AppTheme {
        <Feature>Content(
            uiState = <Feature>UiState.Error("Something went wrong"),
            onEvent = {},
            onNavigateBack = {},
        )
    }
}
```

---

## 🧪 Unit Test Templates

### Test dependencies (build.gradle.kts — commonTest)

```kotlin
dependencies {
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.turbine:turbine:1.1.0") // Flow testing
}
```

### UseCase test

```kotlin
/**
 * Unit tests for [Get<Feature>UseCase].
 */
class Get<Feature>UseCaseTest {

    private val repository: <Feature>Repository = mockk()
    private val useCase = Get<Feature>UseCase(repository)

    @Test
    fun `given repository returns items, when invoked, then returns filtered and sorted list`() =
        runTest {
            // Arrange
            val rawItems = listOf(
                fake<DomainModel>(id = "1", name = "B", isActive = true),
                fake<DomainModel>(id = "2", name = "A", isActive = true),
                fake<DomainModel>(id = "3", name = "C", isActive = false), // filtered out
            )
            coEvery { repository.get<Feature>() } returns rawItems

            // Act
            val result = useCase()

            // Assert
            assertTrue(result.isSuccess)
            val items = result.getOrThrow()
            assertEquals(2, items.size)
            assertEquals("A", items[0].name)
            assertEquals("B", items[1].name)
        }

    @Test
    fun `given repository throws, when invoked, then returns failure`() = runTest {
        val error = IOException("Network unavailable")
        coEvery { repository.get<Feature>() } throws error

        val result = useCase()

        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }
}
```

### ViewModel test

```kotlin
/**
 * Unit tests for [<Feature>ViewModel].
 *
 * Uses [Turbine] to assert on [StateFlow] emissions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class <Feature>ViewModelTest {

    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private val get<Feature>UseCase: Get<Feature>UseCase = mockk()
    private lateinit var viewModel: <Feature>ViewModel

    @BeforeEach
    fun setUp() {
        viewModel = <Feature>ViewModel(get<Feature>UseCase)
    }

    @Test
    fun `given use case succeeds, when ViewModel initialised, then state is Loading then Success`() =
        runTest {
            val items = listOf(fake<DomainModel>())
            coEvery { get<Feature>UseCase() } returns Result.success(items)

            viewModel.uiState.test {
                assertEquals(<Feature>UiState.Loading, awaitItem())
                advanceUntilIdle()
                assertEquals(<Feature>UiState.Success(items), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `given use case fails, when ViewModel initialised, then state is Error`() = runTest {
        coEvery { get<Feature>UseCase() } returns Result.failure(RuntimeException("Oops"))

        viewModel.uiState.test {
            awaitItem() // Loading
            advanceUntilIdle()
            val error = awaitItem() as <Feature>UiState.Error
            assertEquals("Oops", error.message)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given Refresh event, when dispatched, then reloads data`() = runTest {
        val items = listOf(fake<DomainModel>())
        coEvery { get<Feature>UseCase() } returns Result.success(items)

        advanceUntilIdle()
        viewModel.onEvent(<Feature>Event.Refresh)
        advanceUntilIdle()

        coVerify(exactly = 2) { get<Feature>UseCase() }
    }
}
```

### MainCoroutineRule helper

```kotlin
/**
 * JUnit rule that replaces [Dispatchers.Main] with a [TestCoroutineDispatcher]
 * for the duration of each test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainCoroutineRule(
    val testDispatcher: TestCoroutineDispatcher = TestCoroutineDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) {
        Dispatchers.resetMain()
        testDispatcher.cleanupTestCoroutines()
    }
}
```

### Repository test

```kotlin
/**
 * Unit tests for [<Feature>RepositoryImpl].
 */
class <Feature>RepositoryImplTest {

    private val remoteDataSource: <Feature>RemoteDataSource = mockk()
    private val localDataSource: <Feature>LocalDataSource = mockk()
    private val mapper = <Feature>Mapper()
    private val repository = <Feature>RepositoryImpl(remoteDataSource, localDataSource, mapper)

    @Test
    fun `given local cache is empty, when get<Feature>, then fetches from remote and caches`() =
        runTest {
            coEvery { localDataSource.getAll() } returns emptyList()
            val remoteDto = listOf(fake<Feature>Dto())
            coEvery { remoteDataSource.fetchAll() } returns remoteDto
            coEvery { localDataSource.saveAll(any()) } just Runs

            val result = repository.get<Feature>()

            assertEquals(1, result.size)
            coVerify(exactly = 1) { remoteDataSource.fetchAll() }
            coVerify(exactly = 1) { localDataSource.saveAll(remoteDto) }
        }
}
```

---

## 💉 Dependency Injection — Koin Templates

### Feature module

```kotlin
/**
 * Koin DI module for the <Feature> feature.
 *
 * Registers data sources, mapper, repository, use cases, and ViewModel.
 */
val <feature>Module = module {

    // Data sources
    single { <Feature>RemoteDataSource(httpClient = get()) }
    single { <Feature>LocalDataSource(database = get()) }

    // Mapper
    single { <Feature>Mapper() }

    // Repository — bind impl to interface
    single<<Feature>Repository> {
        <Feature>RepositoryImpl(
            remoteDataSource = get(),
            localDataSource = get(),
            mapper = get(),
        )
    }

    // Use cases (factory = stateless, cheap to create)
    factory { Get<Feature>UseCase(repository = get()) }
    factory { Create<Feature>UseCase(repository = get()) }
    factory { Delete<Feature>UseCase(repository = get()) }

    // ViewModel
    viewModel {
        <Feature>ViewModel(get<Feature>UseCase = get())
    }
}
```

### App root module

```kotlin
/**
 * Root Koin module — includes all feature and infrastructure modules.
 * Pass to [startKoin] in your Application class or platform entry point.
 */
val appModule = module {
    includes(
        networkModule,
        databaseModule,
        <feature>Module,
        // add more feature modules here
    )
}
```

### Android Application setup

```kotlin
/**
 * Application entry point.
 * Initialises Koin so all dependencies are available throughout the app lifecycle.
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModule)
        }
    }
}
```

### Shared (commonMain) Koin initialisation

```kotlin
/**
 * Initialises Koin for shared Compose Multiplatform code.
 *
 * Call from each platform's entry point and pass platform-specific
 * modules (e.g. database driver) via [platformModules].
 *
 * @param platformModules Platform-specific Koin modules to include.
 */
fun initKoin(platformModules: List<Module> = emptyList()) {
    startKoin {
        modules(appModule + platformModules)
    }
}
```
