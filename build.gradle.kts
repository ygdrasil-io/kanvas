// Root build intentionally contains no custom architecture-test or report-generation tasks.

val webgpuKtypesVersion = "0.0.10-20260716.185724-3"

// wgpu4k-toolkit July ABI and all four ktypes artifacts must advance together.
allprojects {
    configurations.configureEach {
        resolutionStrategy.force(
            "io.ygdrasil:webgpu-ktypes:$webgpuKtypesVersion",
            "io.ygdrasil:webgpu-ktypes-jvm:$webgpuKtypesVersion",
            "io.ygdrasil:webgpu-ktypes-descriptors:$webgpuKtypesVersion",
            "io.ygdrasil:webgpu-ktypes-descriptors-jvm:$webgpuKtypesVersion",
        )
    }
}
