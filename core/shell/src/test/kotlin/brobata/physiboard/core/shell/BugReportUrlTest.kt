package brobata.physiboard.core.shell

import kotlin.test.Test
import kotlin.test.assertTrue

/** spec: app-shell.md SS15.1, SS29 T26. */
class BugReportUrlTest {

    @Test
    fun `T26 the url carries the template, prefilled version, device label and three diagnostics lines`() {
        val url = BugReportUrl.build(
            BugReportContext(
                versionName = "2.0.7",
                model = TitanModel.TITAN_2_ELITE,
                androidRelease = "15",
                androidSdkInt = 35,
                buildDisplay = "PQ3A.190801.002",
                manufacturer = "Unihertz",
                deviceModel = "Titan 2 Elite",
            ),
        )
        assertTrue(url.startsWith("https://github.com/brobata/physiboard/issues/new?"))
        assertTrue(url.contains("template=bug.yml"))
        assertTrue(url.contains("app_version=2.0.7"))
        assertTrue(url.contains("device=Unihertz+Titan+2+Elite") || url.contains("device=Unihertz%20Titan%202%20Elite"))
        assertTrue(url.contains("android%3D15"))
        assertTrue(url.contains("build%3DPQ3A.190801.002"))
        assertTrue(url.contains("model%3DUnihertz+Titan+2+Elite") || url.contains("model%3DUnihertz%20Titan%202%20Elite"))
    }
}
