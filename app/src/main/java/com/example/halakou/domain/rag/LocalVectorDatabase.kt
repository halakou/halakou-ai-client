package com.example.halakou.domain.rag

import kotlin.math.sqrt

data class DocumentChunk(
    val id: String,
    val title: String,
    val content: String,
    val vector: Map<String, Float>
)

data class SimilarityResult(
    val chunk: DocumentChunk,
    val score: Float
)

/**
 * On-Device Vector Similarity & Local RAG Engine.
 * 
 * Generates lightweight term-frequency vector embeddings and computes
 * cosine similarity locally on the device with zero cloud dependency.
 */
class LocalVectorDatabase {

    private val documentStore = mutableListOf<DocumentChunk>()

    init {
        // Pre-populate with halakou architecture & quick knowledge base
        indexDocument(
            id = "halakou_arch",
            title = "halakou Architecture",
            text = "halakou is an autonomous AI client with Cloudflare Edge Proxy, self-healing endpoints, and AES-256-GCM hardware keystore encryption."
        )
        indexDocument(
            id = "circuit_breaker",
            title = "Circuit Breaker",
            text = "The zero-dependency circuit breaker trips on HTTP 429 rate limits or HTTP 403 quota errors and seamlessly reroutes to fallback models."
        )
    }

    fun indexDocument(id: String, title: String, text: String) {
        val vector = computeTfVector(text)
        documentStore.removeAll { it.id == id }
        documentStore.add(DocumentChunk(id, title, text, vector))
    }

    fun searchSimilar(query: String, topK: Int = 3): List<SimilarityResult> {
        val queryVector = computeTfVector(query)
        if (queryVector.isEmpty() || documentStore.isEmpty()) return emptyList()

        return documentStore.map { doc ->
            val score = cosineSimilarity(queryVector, doc.vector)
            SimilarityResult(doc, score)
        }
            .filter { it.score > 0.05f }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun computeTfVector(text: String): Map<String, Float> {
        val tokens = text.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .filter { it.length > 2 }

        if (tokens.isEmpty()) return emptyMap()

        val frequencies = mutableMapOf<String, Int>()
        for (token in tokens) {
            frequencies[token] = (frequencies[token] ?: 0) + 1
        }

        val total = tokens.size.toFloat()
        return frequencies.mapValues { it.value / total }
    }

    private fun cosineSimilarity(v1: Map<String, Float>, v2: Map<String, Float>): Float {
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for ((term, weight1) in v1) {
            dotProduct += weight1 * (v2[term] ?: 0f)
            normA += weight1 * weight1
        }

        for ((_, weight2) in v2) {
            normB += weight2 * weight2
        }

        if (normA == 0f || normB == 0f) return 0f
        return (dotProduct / (sqrt(normA.toDouble()) * sqrt(normB.toDouble()))).toFloat()
    }
}
