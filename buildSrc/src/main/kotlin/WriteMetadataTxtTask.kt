import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class WriteMetadataTxtTask : DefaultTask() {
    @get:Input
    abstract val metadataValue: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = "build"
        description = "Write metadata txt file"
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun executeTask() {
        val tagValue = metadataValue.get()
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            if (tagValue.isEmpty()) {
                delete()
                return
            } else {
                println("Writing release tag: $tagValue to $this")
                writeText(tagValue)
            }
        }
    }
}
