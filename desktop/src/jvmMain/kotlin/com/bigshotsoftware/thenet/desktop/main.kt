package com.bigshotsoftware.thenet.desktop

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.bigshotsoftware.thenet.Greeting

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "TheNet",
    ) {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme {
        var text by remember { mutableStateOf("Hello, World!") }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Button(onClick = {
                text = Greeting().greet()
            }) {
                Text("Click me!")
            }
            Text(text)
        }
    }
}