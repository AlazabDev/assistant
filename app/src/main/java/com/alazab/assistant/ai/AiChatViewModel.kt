package com.alazab.assistant.ai

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.Chat
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

private const val TAG = "AiChatViewModel"

/**
 * ViewModel managing the AI Chat session and conversation history
 * leveraging the Firebase AI SDK (Gemini 3.8 Flash).
 */
class AiChatViewModel : ViewModel() {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    أنت المساعد الذكي المعتمد لمؤسسة العزب (Alazab Assistant)، متخصص في معالجة الاتصالات والرسائل النصية القصيرة (SMS) وإدارة البوابة الرسمية (+201004006620).
                    مهامك الأساسية:
                    1. معالجة وتفسير الرسائل النصية الواردة واستخراج البيانات الحيوية: رموز التحقق (OTP)، أرقام الطلبات، مبالغ التحويلات (مثل فودافون كاش، إنستاباي، والتحويلات المصرفية).
                    2. صياغة وتعديل قوالب الرسائل القصيرة واستبدال المتغيرات القياسية مثل {otp} و{phone} و{name} و{order_id} و{amount}.
                    3. المساعدة في تشخيص وتتبع مسارات إرسال واستقبال الرسائل للبوابة +201004006620.
                    4. الرد على استفسارات المستخدم بأسلوب مهني، دقيق، وسريع، وباللغة العربية الفصحى الواضحة والراقية.
                    """.trimIndent()
                )
            }
        )
    }

    private var chatSession: Chat? = null

    private fun getOrCreateChat(): Chat {
        var chat = chatSession
        if (chat == null) {
            chat = model.startChat()
            chatSession = chat
        }
        return chat
    }

    private val _messages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                id = "welcome",
                text = "مرحباً بك! أنا مساعد العزب الذكي لمعالجة الاتصالات والرسائل. كيف يمكنني مساعدتك اليوم؟ يمكنك كتابة استفسارك أو لصق أي رسالة نصية واردة لتحليلها واستخراج بياناتها.",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun updateInputText(newText: String) {
        _inputText.value = newText
    }

    /**
     * Send message using streaming response from Firebase AI SDK.
     */
    fun sendMessage(customPrompt: String? = null) {
        val promptToSend = (customPrompt ?: _inputText.value).trim()
        if (promptToSend.isBlank() || _isGenerating.value) return

        if (customPrompt == null) {
            _inputText.value = ""
        }
        _errorMessage.value = null

        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            text = promptToSend,
            isUser = true,
            timestamp = System.currentTimeMillis()
        )

        val aiMessageId = UUID.randomUUID().toString()
        val initialAiMessage = ChatMessage(
            id = aiMessageId,
            text = "",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            isStreaming = true
        )

        _messages.value = _messages.value + userMessage + initialAiMessage
        _isGenerating.value = true

        viewModelScope.launch {
            val responseBuilder = StringBuilder()
            try {
                val chat = getOrCreateChat()
                withTimeout(60_000L) {
                    chat.sendMessageStream(promptToSend).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        responseBuilder.append(chunkText)
                        updateMessage(aiMessageId, responseBuilder.toString(), isStreaming = true, isError = false)
                    }
                }

                val finalOutput = responseBuilder.toString()
                if (finalOutput.isNotBlank()) {
                    updateMessage(aiMessageId, finalOutput, isStreaming = false, isError = false)
                } else {
                    updateMessage(aiMessageId, "لم يتم استلام أي نص من نموذج الذكاء الاصطناعي.", isStreaming = false, isError = true)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat request timed out after 60 seconds", e)
                _errorMessage.value = "انتهت مهلة الاتصال بالذكاء الاصطناعي (60 ثانية). يرجى المحاولة مجدداً."
                updateMessage(aiMessageId, "عذراً، استغرقت الاستجابة وقتاً أطول من المتوقع. يمكنك النقر على إعادة المحاولة.", isStreaming = false, isError = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI chat failed", e)
                val msg = e.localizedMessage ?: "فشل في معالجة الطلب بالذكاء الاصطناعي"
                _errorMessage.value = msg
                updateMessage(aiMessageId, "حدث خطأ أثناء معالجة المحادثة: $msg", isStreaming = false, isError = true)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    private fun updateMessage(id: String, newText: String, isStreaming: Boolean, isError: Boolean) {
        _messages.value = _messages.value.map { msg ->
            if (msg.id == id) {
                msg.copy(text = newText, isStreaming = isStreaming, isError = isError)
            } else {
                msg
            }
        }
    }

    /**
     * Clears conversation history and creates a fresh chat session.
     */
    fun clearConversation() {
        chatSession = null
        _errorMessage.value = null
        _messages.value = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                text = "تم بدء جلسة محادثة جديدة. يمكنك إرسال أي نص لمعالجته أو طلب صياغة قوالب الرسائل للبوابة +201004006620.",
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * Analyze an incoming SMS text directly with a quick one-tap action.
     */
    fun analyzeIncomingSms(smsBody: String, sender: String) {
        val prompt = "يرجى تحليل هذه الرسالة الواردة من ($sender):\n\"$smsBody\"\nواستخراج:\n1. نوع الرسالة (OTP، تنبيه بنكي، إشعار دفع، رسالة عادية)\n2. أي رموز تأكيد أو مبالغ مالية أو أرقام حسابات\n3. الرد المقترح إرساله للعميل إن وجد."
        sendMessage(prompt)
    }
}
