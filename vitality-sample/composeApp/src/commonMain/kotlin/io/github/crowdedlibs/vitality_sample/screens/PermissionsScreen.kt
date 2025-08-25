package io.github.crowdedlibs.vitality_sample.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.crowdedlibs.vitality_sample.state.HealthDemoState
import io.github.crowdedlibs.vitality_sample.utils.getDisplayName
import io.github.crowdedlibs.vitality_sample.viewmodel.HealthViewModel
import vitality.HealthDataTypeCapabilityProvider
import vitality.HealthPermission
import vitality.PermissionStatus

@Composable
fun PermissionsScreen(
    state: HealthDemoState,
    viewModel: HealthViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "Health Permissions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Status card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = when (state.permissionStatus) {
                    is PermissionStatus.Granted -> MaterialTheme.colorScheme.primaryContainer
                    is PermissionStatus.Denied -> MaterialTheme.colorScheme.errorContainer
                    is PermissionStatus.NotDetermined -> MaterialTheme.colorScheme.surfaceVariant
                    is PermissionStatus.PartiallyGranted -> MaterialTheme.colorScheme.secondaryContainer
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Current Status",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = when (state.permissionStatus) {
                        is PermissionStatus.Granted -> "All permissions granted"
                        is PermissionStatus.Denied -> "Permissions denied"
                        is PermissionStatus.NotDetermined -> "Permissions not yet requested"
                        is PermissionStatus.PartiallyGranted -> {
                            val granted = state.permissionStatus.granted.size
                            val denied = state.permissionStatus.denied.size
                            "$granted granted, $denied denied"
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Permission selection header with Select All button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Select Permissions",
                style = MaterialTheme.typography.titleMedium
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.selectedPermissions.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.deselectAllPermissions() }
                    ) {
                        Text("Clear")
                    }
                }
                
                Button(
                    onClick = { viewModel.selectAllPermissions() },
                    enabled = state.availablePermissions.isNotEmpty()
                ) {
                    Text("Select All")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Permission list
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(state.availablePermissions.chunked(2)) { permissionPair ->
                // Group read/write permissions for the same data type
                if (permissionPair.isNotEmpty()) {
                    val dataType = permissionPair.first().dataType
                    val hasRead = permissionPair.any { it.accessType == HealthPermission.AccessType.READ }
                    val hasWrite = permissionPair.any { it.accessType == HealthPermission.AccessType.WRITE }
                    val capabilities = HealthDataTypeCapabilityProvider.getCapabilities(dataType)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (!capabilities.canRead && !capabilities.canWrite) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        } else {
                            CardDefaults.cardColors()
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = dataType.getDisplayName(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (capabilities.canRead || capabilities.canWrite) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    }
                                )
                                
                                // Show capability badges
                                if (!capabilities.canRead && !capabilities.canWrite) {
                                    Text(
                                        "Not Available",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            
                            // Show platform notes if any
                            capabilities.platformNotes?.let { notes ->
                                Text(
                                    notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                )
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                if (hasRead && capabilities.canRead) {
                                    val readPermission = permissionPair.find { 
                                        it.accessType == HealthPermission.AccessType.READ 
                                    }!!
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = readPermission in state.selectedPermissions,
                                            onCheckedChange = { 
                                                viewModel.togglePermission(readPermission)
                                            }
                                        )
                                        Text("Read")
                                    }
                                } else if (hasRead && !capabilities.canRead) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = false,
                                            onCheckedChange = { },
                                            enabled = false
                                        )
                                        Text("Read", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                                
                                if (hasWrite && capabilities.canWrite) {
                                    val writePermission = permissionPair.find { 
                                        it.accessType == HealthPermission.AccessType.WRITE 
                                    }!!
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = writePermission in state.selectedPermissions,
                                            onCheckedChange = { 
                                                viewModel.togglePermission(writePermission)
                                            }
                                        )
                                        Text("Write")
                                    }
                                } else if (hasWrite && !capabilities.canWrite) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = false,
                                            onCheckedChange = { },
                                            enabled = false
                                        )
                                        Text("Write", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Request button
        Button(
            onClick = { viewModel.requestPermissions() },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.selectedPermissions.isNotEmpty() && !state.isRequestingPermissions
        ) {
            if (state.isRequestingPermissions) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("Request Selected Permissions")
            }
        }
    }
}