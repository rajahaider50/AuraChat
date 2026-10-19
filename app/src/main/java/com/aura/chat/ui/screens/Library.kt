package com.aura.chat.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.chat.AppContainer
import com.aura.chat.container
import com.aura.chat.data.ConversationWithPreview
import com.aura.chat.data.Personas
import com.aura.chat.data.PromptEntity
import com.aura.chat.ui.components.GlassButton
import com.aura.chat.ui.components.GlassCard
import com.aura.chat.ui.components.GlassDialog
import com.aura.chat.ui.components.GlassIconButton
import com.aura.chat.ui.components.GlassTextField
import com.aura.chat.ui.components.GradientButton
import com.aura.chat.ui.components.IconBadge
import com.aura.chat.ui.components.ScreenScaffold
import com.aura.chat.ui.relativeTime
import com.aura.chat.ui.simpleFactory
import com.aura.chat.ui.theme.LocalAura
import com.aura.chat.ui.toast
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ---------- Personas ----------

@Composable
fun PersonasScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val c = LocalAura.current
    val container = LocalContext.current.container
    val scope = rememberCoroutineScope()
    ScreenScaffold("Choose a persona", onBack) {
        Text(
            "Each persona starts a new chat with its own expertise and tone.",
            color = c.textDim,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(Personas.all, key = { it.id }) { p ->
                GlassCard(
                    onClick = { scope.launch { onOpenChat(container.repo.createConversation(p)) } },
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconBadge(p.icon, size = 48.dp)
                    Spacer(Modifier.height(14.dp))
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(p.tagline, color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- Search ----------

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val results: StateFlow<List<ConversationWithPreview>> = query
        .debounce(180)
        .flatMapLatest { q -> if (q.isBlank()) emptyFlow() else c.repo.conv.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@Composable
fun SearchScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val c = LocalAura.current
    val container = LocalContext.current.container
    val vm: SearchViewModel = viewModel(factory = simpleFactory { SearchViewModel(container) })
    val q by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    ScreenScaffold("Search", onBack) {
        GlassTextField(
            value = q,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            placeholder = "Search titles and messages",
            leading = Icons.Rounded.Search,
        )
        if (q.isBlank()) {
            Hint("Type to search across every conversation.")
        } else if (results.isEmpty()) {
            Hint("No chats match “${q.trim()}”.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(results, key = { it.conversation.id }) { row ->
                    GlassCard(onClick = { onOpenChat(row.conversation.id) }, shape = RoundedCornerShape(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.conversation.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(relativeTime(row.conversation.updatedAt), color = c.textDim, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(row.preview?.replace('\n', ' ').orEmpty(), color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Column(Modifier.fillMaxWidth().padding(top = 64.dp, start = 32.dp, end = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.ChatBubbleOutline, null, tint = LocalAura.current.textDim, modifier = Modifier.width(40.dp).height(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = LocalAura.current.textDim)
    }
}

// ---------- Prompt library ----------

class PromptsViewModel(private val c: AppContainer) : ViewModel() {
    val prompts: StateFlow<List<PromptEntity>> = c.repo.prompts.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(title: String, text: String) {
        viewModelScope.launch {
            c.repo.prompts.insert(PromptEntity(title = title.trim(), text = text, createdAt = System.currentTimeMillis()))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { c.repo.prompts.delete(id) }
    }
}

@Composable
fun PromptsScreen(onBack: () -> Unit, onOpenChat: (String) -> Unit) {
    val c = LocalAura.current
    val ctx = LocalContext.current
    val container = ctx.container
    val vm: PromptsViewModel = viewModel(factory = simpleFactory { PromptsViewModel(container) })
    val prompts by vm.prompts.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }

    ScreenScaffold(
        "Prompt library",
        onBack,
        actions = { GlassIconButton(Icons.Rounded.Add, { adding = true }, contentDescription = "Add prompt") },
    ) {
        if (prompts.isEmpty()) {
            Hint("Save the prompts you reuse, then insert them into any chat with one tap.")
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(prompts, key = { it.id }) { p ->
                GlassCard(shape = RoundedCornerShape(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(p.text))
                            toast(ctx, "Copied")
                        }) { Icon(Icons.Rounded.ContentCopy, "Copy", tint = c.textDim) }
                        IconButton(onClick = { vm.delete(p.id) }) { Icon(Icons.Rounded.Delete, "Delete", tint = c.danger) }
                    }
                    Text(p.text.trim(), color = c.textDim, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    GlassButton(
                        "Use in new chat",
                        {
                            scope.launch {
                                container.pendingPrompt = p.text
                                onOpenChat(container.repo.createConversation(Personas.all.first()))
                            }
                        },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Rounded.Send,
                    )
                }
            }
        }
    }

    if (adding) {
        var title by remember { mutableStateOf("") }
        var text by remember { mutableStateOf("") }
        GlassDialog(
            "New prompt",
            onDismiss = { adding = false },
            confirmText = "Save",
            onConfirm = {
                if (title.isNotBlank() && text.isNotBlank()) {
                    vm.add(title, text)
                    adding = false
                } else toast(ctx, "Add a title and the prompt text")
            },
        ) {
            GlassTextField(title, { title = it }, label = "Title", placeholder = "e.g. Meeting notes")
            Spacer(Modifier.height(12.dp))
            GlassTextField(text, { text = it }, label = "Prompt", placeholder = "Write the prompt…", singleLine = false, minLines = 4, maxLines = 8)
        }
    }
}
