package dev.q25.bbfiles

import java.io.File

/** Files-owned batch transfer, preserving only failed items for a safe retry. */
internal object ClipboardTransfer {
    data class Outcome(
        val operation: FileOperations.Result,
        val remainingSources: List<File>
    )

    fun paste(
        sourceRoot: File,
        sources: List<File>,
        destinationRoot: File,
        destinationFolder: File,
        move: Boolean
    ): Outcome {
        val failures = mutableListOf<String>()
        val remaining = mutableListOf<File>()
        for (source in sources) {
            when (val result = FileOperations.paste(
                sourceRoot, source, destinationRoot, destinationFolder, move
            )) {
                is FileOperations.Result.Success -> Unit
                is FileOperations.Result.Failure -> {
                    remaining.add(source)
                    failures.add("${source.name}: ${result.message}")
                }
            }
        }
        val result = if (failures.isEmpty()) FileOperations.Result.Success()
        else FileOperations.Result.Failure(
            "Transfer incomplete (${failures.size}/${sources.size} failed): " +
                failures.joinToString("; ")
        )
        return Outcome(result, remaining)
    }
}
