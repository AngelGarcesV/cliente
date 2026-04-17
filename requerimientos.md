# 📦 Requerimientos del Cliente JavaFX

## 🧩 1. Descripción General

El cliente será una aplicación de escritorio desarrollada en **JavaFX**, encargada de interactuar con un servidor mediante sockets (TCP o UDP). Su propósito es permitir:

- Comunicación entre usuarios
- Envío y recepción de archivos y mensajes
- Visualización de logs del sistema
- Gestión de documentos almacenados en el servidor

---

## 🔌 2. Conexión al Servidor

El cliente debe permitir:

- Ingresar:
  - Dirección IP del servidor
  - Puerto
  - Protocolo (TCP o UDP)
- Establecer conexión mediante sockets
- Manejar errores:
  - Timeout
  - Servidor no disponible
  - Conexión rechazada

---

## 👥 3. Gestión de Clientes Conectados

- Solicitar lista de clientes al servidor
- Mostrarla en la interfaz gráfica
- Actualizar dinámicamente

---

## 📂 4. Gestión de Documentos

### 📥 Consultar documentos
El cliente debe:

- Solicitar lista de documentos
- Mostrar:
  - Nombre
  - Tamaño
  - Tipo
  - Fecha

---

### 📤 Enviar documentos

Debe permitir:

- Enviar archivos
- Enviar mensajes de texto
- Enviar múltiples archivos simultáneamente
- Manejar archivos grandes (GB)

#### ⚙️ Estrategia de envío

- Envío por partes (chunks)
- Notificar:
  - Inicio
  - Progreso
  - Finalización

---

### 📥 Descargar documentos

El cliente debe:

- Solicitar archivos específicos
- Elegir formato:
  - Original
  - Hash
  - Encriptado

---

## 🧾 5. Logs del Sistema

- Solicitar logs al servidor
- Mostrar:
  - Fecha
  - Usuario
  - Acción
- Permitir descarga o visualización

---

## ⚠️ 6. Manejo de Restricciones

El cliente debe mostrar mensajes cuando:

- El servidor esté lleno
- Se rechace una conexión

Ejemplo:
> "Capacidad máxima alcanzada"

---

## 🧠 7. Protocolo de Comunicación

### ❌ Restricción:
No se permite usar HTTP

### ✔ Requisito:
!!SE DEBE DE PEDIR AL USUARIO SELECCIONAR SI SE QUIERE COMUNICAR POR UTP O TCP¡¡

Ejemplo:

| Código | Acción |
|------|--------|
| 1 | Listar clientes |
| 2 | Listar documentos |
| 3 | Descargar documento |
| 4 | Enviar archivo |
| 5 | Solicitar logs |

### Características:

- Uso de JSON para mensajes
- Archivos:
  - Base64 o binario por partes
- Interpretación de comandos

---

## 🎨 9. Interfaz Gráfica (JavaFX)

### 🖥️ Pantalla principal
- Estado de conexión
- Lista de clientes
- Lista de documentos

---

### 📁 Panel de archivos
- Subir archivo
- Descargar archivo
- Ver detalles

---

### 💬 Panel de mensajes
- Enviar mensajes
- Mostrar respuestas

---

### 📊 Panel de logs
- Visualización tipo tabla

---

### 📐 Requisito adicional:
- Entregar mockups de la interfaz

---

## ⚙️ 10. Requerimientos No Funcionales

### 🔒 Seguridad
- Hash de archivos
- Encriptación
- Validación de integridad

---

### 🚀 Rendimiento
- Soporte para archivos grandes
- Uso eficiente de memoria (streams)
- Envío concurrente

---

### 🔄 Concurrencia
- Manejo de:
  - Envío
  - Recepción
  - Interfaz gráfica

Uso de:
- Threads
- Executors

---

### 🧩 Escalabilidad
- Soportar múltiples conexiones
- No bloquear la UI

---

## 🏗️ 11. Arquitectura Recomendada

### 📦 Capas

#### 1. Presentación
- JavaFX (Vistas y Controladores)

#### 2. Aplicación
- Servicios
- Casos de uso

#### 3. Dominio
- Modelos:
  - Cliente
  - Documento
  - Mensaje

#### 4. Infraestructura
- Sockets (TCP/UDP)
- Base de datos H2
- Serialización JSON

---

## 🧪 12. Pruebas

El cliente debe ser probado con:

- Archivos:
  - Pequeños (KB)
  - Medianos (MB)
  - Grandes (GB)
- Conexión:
  - TCP
  - UDP
- Concurrencia:
  - Múltiples envíos simultáneos

---

## 📄 13. Entregables

- Cliente funcional en JavaFX
- Mockups
- Diagramas:
  - Clases
  - Componentes
  - Dominio
- Diseño por capas
- Documentación del protocolo

---

## ⚠️ 14. Errores a Evitar

- Usar HTTP
- No manejar archivos por partes
- Bloquear el hilo de JavaFX
- No definir protocolo propio
- No separar arquitectura en capas

---

## 🎯 15. Conclusión

El cliente JavaFX es un sistema completo que:

- Se comunica mediante sockets
- Implementa un protocolo propio
- Maneja archivos grandes
- Usa concurrencia
- Tiene persistencia local
- Sigue arquitectura limpia