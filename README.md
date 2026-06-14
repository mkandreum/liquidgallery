<div align="center">

# 🪞 Liquid Glass Gallery

**Galería multimedia con interfaz de cristal líquido**

*Un producto de [Xyon Platforms](https://github.com/mkandreum)*

![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=flat&logo=jetpackcompose&logoColor=white)
![Coil](https://img.shields.io/badge/Coil-FF6F00?style=flat&logo=coil&logoColor=white)

</div>

---

## ¿Qué es Liquid Glass Gallery?

Liquid Glass Gallery es una aplicación Android nativa desarrollada en Kotlin por Xyon Platforms que permite visualizar imágenes y vídeos con una interfaz de **cristal líquido** con efectos de refracción, distorsión tipo lupa y aberración cromática dinámica vía shaders AGSL.

## ✨ Funcionalidades

- **Galería multimedia** con imágenes y vídeos del dispositivo
- **Efecto cristal líquido** con distorsión tipo lupa, blur y aberración cromática en cada barra flotante
- **Reproductor de vídeo** con barra de progreso glass, timer y controles con auto-hide
- **Zoom interactivo** con gestos pinch-to-zoom y doble tap (1x–4x)
- **Filtros de imagen** en tiempo real (brillo, contraste, saturación, rotación) con matriz de color
- **Miniaturas de vídeo** generadas con `MediaMetadataRetriever`
- **Navegación por swipe** con `HorizontalPager` y efecto parallax
- **Modo selección múltiple** con acciones en lote
- **Colecciones inteligentes** por ubicación, favoritos y álbumes personalizados
- **Modo oculto** con autenticación biométrica

## 🛠️ Stack técnico

| Capa | Tecnología |
|------|----------|
| Lenguaje | Kotlin |
| Plataforma | Android (minSdk 34, targetSdk 36) |
| UI | Jetpack Compose + Material3 |
| Imágenes | Coil (thumbnail + full-res) |
| Vídeo | VideoView nativo |
| Shaders | AGSL (RuntimeShader) vía `GraphicsLayer.renderEffect` |
| Persistencia | Room (cache + metadatos) |
| Arquitectura | MVVM (ViewModel + StateFlow) |

## 🚀 Instalación

```bash
git clone https://github.com/mkandreum/liquidgallery.git
cd liquidgallery
# Abrir con Android Studio y compilar
```

## 🏢 Xyon Platforms

Liquid Glass Gallery es un producto desarrollado y mantenido por **Xyon Platforms**, empresa especializada en soluciones digitales para negocios locales y pymes.

> © Xyon Platforms — Todos los derechos reservados
