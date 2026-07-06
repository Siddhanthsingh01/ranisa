package com.example.ui

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class GreetingVoicePlayer(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var audioTrack: AudioTrack? = null
    private val handler = Handler(Looper.getMainLooper())

    fun playGreeting(username: String, onComplete: () -> Unit) {
        val prefs = context.getSharedPreferences("ranisa_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("login_greeting_enabled", true)
        
        if (!isEnabled) {
            onComplete()
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val ringerMode = audioManager.ringerMode
        val isSilent = ringerMode == AudioManager.RINGER_MODE_SILENT || ringerMode == AudioManager.RINGER_MODE_VIBRATE
        
        // Standard Android STREAM_MUSIC volume
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // If silent, play silently or bypass audio but still run animation/timer
        val playAudio = !isSilent && currentVolume > 0

        if (!playAudio) {
            // Wait 4 seconds for the visual animation to finish, then complete
            handler.postDelayed({
                onComplete()
            }, 4000)
            return
        }

        // Play welcome chime first, then play TTS greeting
        try {
            playWelcomeChime {
                playTtsGreeting(username, onComplete)
            }
        } catch (e: Exception) {
            Log.e("GreetingVoicePlayer", "Error playing chime, falling back to direct TTS", e)
            playTtsGreeting(username, onComplete)
        }
    }

    private fun playWelcomeChime(onChimeFinished: () -> Unit) {
        try {
            val sampleRate = 44100
            val durationSeconds = 1.0f
            val numSamples = (sampleRate * durationSeconds).toInt()
            val samples = FloatArray(numSamples)
            
            for (i in 0 until numSamples) {
                val t = i.toFloat() / sampleRate
                // Dual frequencies for a beautiful devotional chime:
                // C5 (523.25 Hz) + E5 (659.25 Hz) + G5 (783.99 Hz)
                val wave = 0.5f * Math.sin(2.0 * Math.PI * 523.25 * t) + 
                             0.3f * Math.sin(2.0 * Math.PI * 659.25 * t) +
                             0.2f * Math.sin(2.0 * Math.PI * 783.99 * t)
                // Soft exponential decay envelope for a bell sound
                val envelope = Math.exp(-2.5 * t)
                samples[i] = (wave * envelope).toFloat()
            }
            
            val pcm = ShortArray(numSamples)
            for (i in samples.indices) {
                pcm[i] = (samples[i] * 32767).toInt().coerceIn(-32768, 32767).toShort()
            }
            
            audioTrack = AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                pcm.size * 2,
                AudioTrack.MODE_STATIC
            )
            audioTrack?.write(pcm, 0, pcm.size)
            audioTrack?.setVolume(0.5f) // Medium volume as requested
            audioTrack?.play()
            
            // Wait 1.2 seconds for the chime sustain to finish, then start TTS
            handler.postDelayed({
                try {
                    audioTrack?.stop()
                    audioTrack?.release()
                    audioTrack = null
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                onChimeFinished()
            }, 1200)
        } catch (e: Exception) {
            Log.e("GreetingVoicePlayer", "Failed to play chime", e)
            onChimeFinished()
        }
    }

    private fun playTtsGreeting(username: String, onComplete: () -> Unit) {
        var completed = false
        val completeAction = {
            if (!completed) {
                completed = true
                onComplete()
                shutdown()
            }
        }

        // 5-second safety timeout so the login screen never gets stuck under any condition
        handler.postDelayed({
            completeAction()
        }, 5000)

        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localTts = tts ?: return@TextToSpeech
                
                // Set language to Hindi
                val localeResult = localTts.setLanguage(Locale("hi", "IN"))
                if (localeResult == TextToSpeech.LANG_MISSING_DATA || localeResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    localTts.setLanguage(Locale("hi"))
                }
                
                // Adjust parameters for a warm, soft female-like TTS voice
                localTts.setSpeechRate(0.85f) // Slightly slower speaking speed (approx 0.9x)
                localTts.setPitch(1.15f)      // Slightly higher pitch for female soft tone
                
                // Seek female voice if available
                try {
                    val voices = localTts.voices
                    if (voices != null) {
                        val hiFemaleVoice = voices.find { voice ->
                            voice.locale.language == "hi" && 
                            (voice.name.lowercase().contains("female") || voice.name.lowercase().contains("f-"))
                        } ?: voices.find { it.locale.language == "hi" }
                        
                        hiFemaleVoice?.let { localTts.voice = it }
                    }
                } catch (e: Exception) {
                    Log.e("GreetingVoicePlayer", "Failed to select specific voice", e)
                }

                // Listen to when speaking completes
                localTts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    
                    override fun onDone(utteranceId: String?) {
                        handler.post { completeAction() }
                    }

                    override fun onError(utteranceId: String?) {
                        handler.post { completeAction() }
                    }
                })

                // Format display name for natural Hindi speech
                val displayName = when {
                    username.contains("(") -> username.substringBefore("(").trim()
                    else -> username
                }
                // Use phonetic spelling "क्रिष्ण" to guarantee "Krish-na" pronunciation with a soft "ri" sound, 
                // avoiding any dialectal "Krushna", "Krisna", or "Krishnaa" pronunciations.
                val greetingText = "हरे क्रिष्ण, $displayName जी। रानीसा में आपका हार्दिक स्वागत है। आपका दिन मंगलमय हो।"
                
                val params = android.os.Bundle().apply {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "greeting_id")
                }
                localTts.speak(greetingText, TextToSpeech.QUEUE_FLUSH, params, "greeting_id")
            } else {
                Log.e("GreetingVoicePlayer", "TTS Initialization failed")
                completeAction()
            }
        }
    }

    fun shutdown() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
