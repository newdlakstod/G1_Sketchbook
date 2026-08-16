$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$componentPath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookCover.kt'
$themePath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/ui/theme/Theme.kt'
$homePath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/ui/main/MainScreen.kt'
$listPath = Join-Path $projectRoot 'app/src/main/java/com/g1/sketchbook/sketchbook/SketchbookScreens.kt'

function Assert-Contains([string]$Text, [string]$Needle, [string]$Message) {
    if (-not $Text.Contains($Needle)) { throw $Message }
}

if (-not (Test-Path -LiteralPath $componentPath)) { throw 'SketchbookCover.kt is missing.' }
$component = Get-Content -Raw -Encoding UTF8 -LiteralPath $componentPath
$theme = Get-Content -Raw -Encoding UTF8 -LiteralPath $themePath
$home = Get-Content -Raw -Encoding UTF8 -LiteralPath $homePath
$list = Get-Content -Raw -Encoding UTF8 -LiteralPath $listPath

Assert-Contains $component 'Color(0xFFFFBF2A)' 'Default cover color must be #FFBF2A.'
Assert-Contains $component '0.20f' 'Solid-color spine opacity must be 20%.'
Assert-Contains $component '0.70f' 'Image spine opacity must be 70%.'
Assert-Contains $component '0.09f' 'Spine width must be 9%.'
Assert-Contains $component 'coverImage: Painter? = null' 'Optional image cover API is missing.'
Assert-Contains $home 'SketchbookCover(' 'Home must use SketchbookCover.'
Assert-Contains $list 'SketchbookCover(' 'Sketchbook list must use SketchbookCover.'
if ($theme.Contains('CoverColors')) { throw 'Theme.kt still contains CoverColors.' }
if ($home.Contains('R.drawable.mascot_duck') -or $list.Contains('R.drawable.mascot_duck')) {
    throw 'A sketchbook cover still renders mascot_duck.'
}
