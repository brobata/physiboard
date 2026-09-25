# PhysiBoard 3.0: consumer keep rules for :device:privileged, merged automatically into any app
# that depends on this module (docs/release.md, "Shrinking"). Every class below is instantiated by
# the system from its name in AndroidManifest.xml, not constructed by this app's own code, so R8's
# ordinary reachability analysis cannot see they are used.

# broker-privileged-toolbox.md SS4.1 step 3: a foreground service the pairing flow starts.
-keep class brobata.physiboard.device.privileged.broker.PairingWatcherService { *; }

# device-backlight-ring.md SS2.2: the Quick Settings tile, bound by android.service.quicksettings.
-keep class brobata.physiboard.device.privileged.backlight.KeyboardBacklightTileService { *; }

# device-backlight-ring.md SS5.2: allow-listed and bound by class name as a notification listener.
-keep class brobata.physiboard.device.privileged.ring.NotificationRingListener { *; }

# device-backlight-ring.md SS5.6: launched by class name from the listener above.
-keep class brobata.physiboard.device.privileged.ring.NotificationRingActivity { *; }
