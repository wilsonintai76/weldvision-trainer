package com.example.calibration

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File

/**
 * Persistent store for calibration profiles.
 *
 * Profiles are keyed by (bracketId, studentId) so that multiple students
 * can calibrate on the same shared device without overwriting each other.
 *
 * File layout:
 *   filesDir/calibration_profiles/
 *     index.json                          ← master index
 *     {bracketId}_{studentId}.json        ← per-student profiles
 */
class ProfilePersistenceStore(private val context: Context) {

    private val profilesDir: File by lazy {
        File(context.filesDir, "calibration_profiles").also { it.mkdirs() }
    }

    private val indexFile: File by lazy {
        File(profilesDir, "index.json")
    }

    // ── Index data structures ────────────────────────

    data class ProfileIndex(
        val entries: MutableMap<String, ProfileEntry> = mutableMapOf()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            val entriesJson = JSONObject()
            for ((key, entry) in entries) {
                entriesJson.put(key, entry.toJson())
            }
            put("entries", entriesJson)
        }

        companion object {
            fun fromJson(json: JSONObject): ProfileIndex {
                val entries = mutableMapOf<String, ProfileEntry>()
                val entriesJson = json.optJSONObject("entries")
                entriesJson?.let {
                    for (key in it.keys()) {
                        entries[key] = ProfileEntry.fromJson(it.getJSONObject(key))
                    }
                }
                return ProfileIndex(entries)
            }
        }
    }

    data class ProfileEntry(
        val bracketId: String,
        val studentId: String,
        val deviceModel: String,
        val createdAt: Long
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("bracketId", bracketId)
            put("studentId", studentId)
            put("deviceModel", deviceModel)
            put("createdAt", createdAt)
        }

        companion object {
            fun fromJson(json: JSONObject): ProfileEntry {
                return ProfileEntry(
                    bracketId = json.getString("bracketId"),
                    studentId = json.getString("studentId"),
                    deviceModel = json.optString("deviceModel", ""),
                    createdAt = json.optLong("createdAt", 0L)
                )
            }
        }
    }

    // ── Key generation ───────────────────────────────

    private fun profileKey(bracketId: String, studentId: String): String {
        return "${bracketId}_${studentId}"
    }

    private fun profileFile(bracketId: String, studentId: String): File {
        return File(profilesDir, "${profileKey(bracketId, studentId)}.json")
    }

    // ── Index management ─────────────────────────────

    private fun loadIndex(): ProfileIndex {
        if (!indexFile.exists()) return ProfileIndex()
        return try {
            ProfileIndex.fromJson(JSONObject(indexFile.readText()))
        } catch (e: Exception) {
            ProfileIndex()
        }
    }

    private fun saveIndex(index: ProfileIndex) {
        indexFile.writeText(index.toJson().toString(2))
    }

    // ── Save (keyed by bracket + student) ────────────

    fun save(
        bracketId: String,
        studentId: String,
        camera: CameraProfile?,
        tcp: TcpProfile?,
        workpiece: WorkpieceProfile?
    ) {
        val profile = CalibrationProfile(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            camera = camera,
            tcp = tcp,
            workpiece = workpiece
        )

        val file = profileFile(bracketId, studentId)
        file.writeText(profile.toJson().toString(2))

        // Update master index
        val index = loadIndex()
        index.entries[profileKey(bracketId, studentId)] = ProfileEntry(
            bracketId = bracketId,
            studentId = studentId,
            deviceModel = profile.deviceModel,
            createdAt = profile.createdAt
        )
        saveIndex(index)
    }

    // ── Load (keyed) ─────────────────────────────────

    fun load(bracketId: String, studentId: String): CalibrationProfile? {
        val file = profileFile(bracketId, studentId)
        if (!file.exists()) return null
        return try {
            CalibrationProfile.fromJson(JSONObject(file.readText()))
        } catch (e: Exception) {
            null
        }
    }

    // ── Load with diagnostics ────────────────────────

    sealed class LoadResult {
        data class Valid(val profile: CalibrationProfile) : LoadResult()
        data class Corrupted(val path: String, val error: String) : LoadResult()
        object NotFound : LoadResult()
    }

    fun loadWithDiagnostics(bracketId: String, studentId: String): LoadResult {
        val file = profileFile(bracketId, studentId)
        if (!file.exists()) return LoadResult.NotFound
        return try {
            val raw = file.readText()
            val profile = CalibrationProfile.fromJson(JSONObject(raw))
            LoadResult.Valid(profile)
        } catch (e: Exception) {
            LoadResult.Corrupted(file.absolutePath, e.message ?: "Unknown error")
        }
    }

    // ── Quick gate ───────────────────────────────────

    fun isSystemCalibrated(bracketId: String, studentId: String): Boolean {
        val profile = load(bracketId, studentId) ?: return false
        val tcpOk = profile.tcp?.quality?.isValid() ?: false
        val wpOk  = profile.workpiece?.valid ?: false
        return tcpOk && wpOk
    }

    // ── List all stored profiles ─────────────────────

    fun listProfiles(): List<ProfileEntry> {
        return loadIndex().entries.values.toList()
    }

    // ── Delete a profile ─────────────────────────────

    fun delete(bracketId: String, studentId: String) {
        val file = profileFile(bracketId, studentId)
        if (file.exists()) file.delete()

        val index = loadIndex()
        index.entries.remove(profileKey(bracketId, studentId))
        saveIndex(index)
    }

    // ── Clear everything ─────────────────────────────

    fun clearAll() {
        profilesDir.listFiles()?.forEach { it.delete() }
        profilesDir.delete()
    }
}
