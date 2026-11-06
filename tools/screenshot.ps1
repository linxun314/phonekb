
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
$b = New-Object System.Drawing.Bitmap([System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Width, [System.Windows.Forms.Screen]::PrimaryScreen.Bounds.Height)
$g = [System.Drawing.Graphics]::FromImage($b)
$g.CopyFromScreen(0, 0, 0, 0, $b.Size)
$b.Save("C:\Users\22453\Desktop\phoneKB\build\screen.png", [System.Drawing.Imaging.ImageFormat]::Png)
"saved " + $b.Width + "x" + $b.Height
