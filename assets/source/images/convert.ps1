# Converts the original images listed in manifest.json into app-sized PNGs under
# apps/mobile/assets/images. Run from any directory:
#   powershell -ExecutionPolicy Bypass -File assets/source/images/convert.ps1
# Rules are described in README.md next to this script.

Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;

public static class AlphaBounds
{
    // Bounding box of pixels that are not (almost) fully transparent.
    public static Rectangle Find(Bitmap bitmap)
    {
        BitmapData data = bitmap.LockBits(new Rectangle(0, 0, bitmap.Width, bitmap.Height),
            ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
        byte[] bytes = new byte[data.Stride * data.Height];
        Marshal.Copy(data.Scan0, bytes, 0, bytes.Length);
        bitmap.UnlockBits(data);

        int minX = bitmap.Width, minY = bitmap.Height, maxX = -1, maxY = -1;
        for (int y = 0; y < bitmap.Height; y++)
        {
            for (int x = 0; x < bitmap.Width; x++)
            {
                if (bytes[y * data.Stride + x * 4 + 3] <= 8) continue;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        if (maxX < 0) return new Rectangle(0, 0, bitmap.Width, bitmap.Height);
        return Rectangle.FromLTRB(minX, minY, maxX + 1, maxY + 1);
    }
}
'@

$sourceDir = $PSScriptRoot
$appImages = Join-Path $PSScriptRoot "..\..\..\apps\mobile\assets\images"
$manifest = Get-Content (Join-Path $sourceDir "manifest.json") -Raw -Encoding UTF8 | ConvertFrom-Json
# Share of the output square left empty around the picture.
$padding = 0.04

function Get-Bounds($path) {
    $bitmap = New-Object System.Drawing.Bitmap $path
    try { return [AlphaBounds]::Find($bitmap) } finally { $bitmap.Dispose() }
}

function Convert-Image($sourcePath, $outPath, $bounds, $size) {
    $source = New-Object System.Drawing.Bitmap $sourcePath
    $target = New-Object System.Drawing.Bitmap $size, $size, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        $graphics.Clear([System.Drawing.Color]::Transparent)
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        # Fit the cropped area into the square, centered, keeping its aspect ratio.
        $inner = $size * (1 - 2 * $padding)
        $scale = [Math]::Min($inner / $bounds.Width, $inner / $bounds.Height)
        $width = $bounds.Width * $scale
        $height = $bounds.Height * $scale
        $destination = New-Object System.Drawing.RectangleF (($size - $width) / 2), (($size - $height) / 2), $width, $height
        $graphics.DrawImage($source, $destination, $bounds, [System.Drawing.GraphicsUnit]::Pixel)
        New-Item -ItemType Directory -Force (Split-Path $outPath) | Out-Null
        $target.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $graphics.Dispose(); $target.Dispose(); $source.Dispose()
    }
}

$ready = $manifest.images | Where-Object { $_.status -eq "ready" }
foreach ($groupName in ($ready | ForEach-Object { $_.group } | Sort-Object -Unique)) {
    $group = $manifest.groups.$groupName
    $entries = $ready | Where-Object { $_.group -eq $groupName }

    # "shared" crops every image of the group with one box so relative sizes
    # (e.g. flames growing with the heat level) are preserved.
    $shared = $null
    if ($group.trim -eq "shared") {
        foreach ($entry in $entries) {
            $bounds = Get-Bounds (Join-Path $sourceDir $entry.source)
            if ($null -eq $shared) { $shared = $bounds } else { $shared = [System.Drawing.Rectangle]::Union($shared, $bounds) }
        }
    }

    foreach ($entry in $entries) {
        $sourcePath = Join-Path $sourceDir $entry.source
        if ($null -ne $shared) { $bounds = $shared } else { $bounds = Get-Bounds $sourcePath }
        $outPath = Join-Path (Join-Path $appImages $group.outDir) $entry.out
        Convert-Image $sourcePath $outPath $bounds $group.size
        Write-Output ("{0,-28} -> {1}\{2} ({3}px)" -f $entry.key, $group.outDir, $entry.out, $group.size)
    }
}
