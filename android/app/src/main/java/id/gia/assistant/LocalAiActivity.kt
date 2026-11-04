package id.gia.assistant

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.*
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class LocalAiActivity : Activity() {
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
 private val modelUrl="https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf"
 private lateinit var status:TextView
 private lateinit var chat:TextView
 private lateinit var input:EditText
 private lateinit var download:Button
 private lateinit var load:Button
 private lateinit var send:Button
 private var model:LlamaModel?=null
 private val history=StringBuilder()
 private val modelFile:File get()=File(getExternalFilesDir("models")?:filesDir,"gia-qwen-1.5b-q4.gguf")

 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);makeUi();refresh()}
 private fun makeUi(){
  val root=LinearLayout(this).apply{orientation=1;setPadding(20,18,20,14);setBackgroundColor(Color.rgb(9,15,32))}
  root.addView(TextView(this).apply{text="GIA • AI LOKAL";textSize=24f;setTextColor(Color.WHITE)})
  status=TextView(this).apply{text="AI pribadi yang berjalan di HP";setTextColor(Color.LTGRAY);setPadding(0,10,0,12)}
  download=Button(this).apply{text="1. Unduh model AI (sekitar 1 GB)";setOnClickListener{downloadModel()}}
  load=Button(this).apply{text="2. Muat model AI";setOnClickListener{loadModel()}}
  chat=TextView(this).apply{text="Halo kawan! Unduh model saat tersambung Wi-Fi, lalu muat model untuk mulai mengobrol offline.";textSize=16f;setTextColor(Color.WHITE);setPadding(4,14,4,14)}
  val scroll=ScrollView(this).apply{addView(chat)}
  input=EditText(this).apply{hint="Tulis pesan untuk GIA";setTextColor(Color.WHITE);setHintTextColor(Color.GRAY);minLines=2;setPadding(12,8,12,8)}
  send=Button(this).apply{text="Kirim pesan";setOnClickListener{sendMessage()}}
  root.addView(status);root.addView(download);root.addView(load)
  root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));root.addView(input);root.addView(send)
  setContentView(root)
 }
 private fun refresh(){status.text=if(modelFile.exists())"Model sudah ada: ${modelFile.length()/(1024*1024)} MB" else "Model belum diunduh. Unduh sekali melalui Wi-Fi.";load.isEnabled=modelFile.exists()}
 private fun downloadModel(){
  download.isEnabled=false;status.text="Mengunduh model dari Hugging Face..."
  scope.launch{
   try{
    withContext(Dispatchers.IO){
     modelFile.parentFile?.mkdirs()
     val c=URL(modelUrl).openConnection() as HttpURLConnection
     c.connectTimeout=20000;c.readTimeout=60000;c.instanceFollowRedirects=true
     try{
      c.connect();if(c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
      val total=c.contentLengthLong;val temp=File(modelFile.parentFile,modelFile.name+".part")
      c.inputStream.use{src->temp.outputStream().buffered().use{dst->
       val buf=ByteArray(1024*1024);var n:Int;var got=0L
       while(src.read(buf).also{n=it}!=-1){dst.write(buf,0,n);got+=n
        if(total>0){val pct=(got*100/total).toInt();withContext(Dispatchers.Main){status.text="Unduhan model: ${pct}%"}}
       }
      }}
      if(!temp.renameTo(modelFile)){temp.copyTo(modelFile,true);temp.delete()}
     }finally{c.disconnect()}
    }
    refresh();Toast.makeText(this@LocalAiActivity,"Model berhasil diunduh",Toast.LENGTH_LONG).show()
   }catch(e:Exception){status.text="Unduhan gagal: ${e.message}";Toast.makeText(this@LocalAiActivity,"Periksa Wi-Fi lalu coba lagi",Toast.LENGTH_LONG).show()}
   finally{download.isEnabled=true}
  }
 }
 private fun loadModel(){
  load.isEnabled=false;status.text="Memuat model ke RAM..."
  scope.launch{
   try{
    model=withContext(Dispatchers.Default){Llama.loadModel(modelFile.absolutePath,LlamaConfig(contextSize=2048,threads=3,gpuLayers=0))}
    status.text="GIA aktif. Percakapan diproses di HP."
    chat.append("\n\nGIA: Aku sudah siap, kawan. Kita bisa mengobrol tanpa internet.")
   }catch(e:Exception){status.text="Gagal memuat model: ${e.message}";load.isEnabled=true}
  }
 }
 private fun sendMessage(){
  val q=input.text.toString().trim();val active=model
  if(q.isEmpty())return
  if(active==null||!active.isLoaded){Toast.makeText(this,"Muat model terlebih dahulu",Toast.LENGTH_SHORT).show();return}
  input.setText("");send.isEnabled=false;chat.append("\n\nKamu: ${q}\n\nGIA sedang berpikir...")
  scope.launch{
   try{
    val result=Llama.complete(active,prompt=history.toString()+"\nUser: "+q,systemPrompt="Namamu GIA. Kamu asisten AI perempuan yang ramah, lembut, cerdas, dan berbicara bahasa Indonesia seperti sahabat. Jawab jelas dan jujur.",maxTokens=256)
    history.append("\nUser: ").append(q).append("\nGIA: ").append(result.text)
    if(history.length>6000)history.delete(0,history.length-4500)
    chat.append("\nGIA: ${result.text}")
    status.text="AI lokal aktif • ${String.format("%.1f",result.tokensPerSecond)} token/detik"
   }catch(e:Exception){chat.append("\nGIA: Maaf, terjadi kesalahan: ${e.message}")}
   finally{send.isEnabled=true}
  }
 }
 override fun onDestroy(){model?.let{Llama.releaseModel(it)};model=null;scope.cancel();super.onDestroy()}
}
