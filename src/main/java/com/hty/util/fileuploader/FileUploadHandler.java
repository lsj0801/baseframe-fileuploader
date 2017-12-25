package com.hty.util.fileuploader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
/**
 * 文件上传处理类<br>
 * 使用者只需继承此类，然后在公用方法里面参与上传事件的处理即可<br>
 * 最简单的过程即为覆写onFileField()函数
 * @author Hetianyi
 * @version 1.0
 */
public class FileUploadHandler {
	
	protected HttpServletRequest request;
	protected HttpServletResponse response;
	/**
	 * 上传字节缓冲区大小，默认为1024B，缓冲区不能小于分割线的大小，建议不小于1024B
	 */
	protected int buffersize = 1024;
	/**
	 * 最大上传大小,最大文件上传大小,默认为50M
	 */
	protected int maxUploadSize = 52428800;
	/**
	 * CPU限频<br>
	 * 使用方法：当读取{@linkplain #cpulimit}次缓冲区后,<br>
	 * 上传会自动休眠 {@linkplain #bufsleep} ms,以此来降低单个任务CPU使用率
	 */
	protected int cpulimit = 1;
	/**
	 * 每次刷新缓存后线程休眠时间(ms)（控制上传速度），上传速度理论上最大为[1000/cpulimit * buffersize] b/s
	 */
	protected int bufsleep = 1;
	/**
	 * 当前上传的文件名
	 */
	protected String filename;
	/**
	 * 表单分割线
	 */
	protected String boundary;
	/**
	 * 可选参数：当前正在上传的文件<br>
	 * 用户可以在方法域里面自行定义File对象，将OutputStream ops绑定到自定义的File对象
	 */
	protected File file;
	/**
	 * 读取的参数键值
	 */
	protected Map<String, String> paraMap;
	/**
	 * 当前文件的输出流
	 */
	protected OutputStream ops;
	/**
	 * 当前上传的总字节数
	 */
	protected long totalbytes = 0;
	/**
	 * 缓冲区刷新次数
	 */
	private long buffertimes = 0;
	/**
	 * 初始化参数.初始化字节缓冲区和最大上传文件大小[可选项]
	 */
	protected void initConfig(){
		this.buffersize = 1024;
		this.maxUploadSize = 1024*1024*50;
		this.cpulimit = 1024;
		this.bufsleep = 1;
	}
	/**
	 * 读取到文本参数时用户可以调用此方法，
	 * 再决定如何进行处理文件的保存[可选项]<br>
	 * @param paraName 参数名
	 * @param paraValue 参数值
	 * @return boolean <br>
	 *  true : 程序继续执行 <br>
	 *  false : 程序中断执行,读取结束
	 */
	protected boolean onTextField(String paraName, String paraValue) throws Exception{
		return true;
	}
	
	/**
	 * 读取到文件时调用此方法
	 * 一般建议将文本域放在文件前面<br>
	 * 在程序读取到文本域之后可以决定文件的存储方式、存储位置等[必选项]<br>
	 * @param filename 文件名称(文件名已处理，不会带有盘符)
	 */
	protected boolean onFileField(String filename) throws Exception{
		return true;
	}
	/**
	 * 当一个文件读取结束后调用此方法[可选项]<br>
	 * 用户可以在此关闭当前文件的输出流<br>
	 * PS:同一时刻只会上传一个文件, 因此不同担心线程安全问题
	 * @throws IOException 
	 */
	protected void onFileEnd() throws Exception{ }
	/**
	 * 读取到文件输入流字节时调用此方法[可选项]<br>
	 * 读取一个文件可能多次调用此方法<br>
	 * 前提假设是用户已之前定义好文件输出流
	 * @param b 读取的文件字节数组
	 * @throws IOException 
	 */
	protected void writeFile(byte[] b) throws Exception{
		writeFile(b, 0, b.length);
	}
	/**
	 * 读取到文件输入流字节时调用此方法[可选项]<br>
	 * 读取一个文件可能多次调用此方法<br>
	 * 前提假设是用户已之前定义好文件输出流
	 * @param b 读取的文件字节数组
	 * @param start 起始字节位置
	 * @param length 有效字节长度
	 * @throws IOException
	 */
	protected void writeFile(byte[] b, int start, int length) throws Exception{
		if(null != ops){
			if(buffertimes % cpulimit == 0 && bufsleep > 0) {
				Thread.sleep(bufsleep);
			}
			ops.write(b, start, length);
			totalbytes += length;
			buffertimes++;
		}
	}
	/**
	 * 当客户端刷新或者程序异常导致输入流中断时调用该方法.[可选项]<br>
	 * 此时会自动删除未上传成功的文件
	 * @throws IOException 
	 */
	protected void onRequestInputStreamInterrupt() throws Exception{
		//上传失败，删除文件列队 的最后一个文件
		if(null != file && file.exists()){
			file.delete();
		}
	}
	/**
	 * 当上传发生未处理的异常调用此方法[可选项]<br><br>
	 * 该异常会导致上传终止
	 * @param e Exception
	 * @return boolean <br>
	 *  true : 程序继续执行 <br>
	 *  false : 程序中断执行,读取结束
	 */
	protected boolean onError(Exception e) throws Exception{
		return false;
	}
	
	/**
	 * 当上传大小超过限制时调用此方法[可选项]
	 * @return boolean <br>
	 *  true : 程序继续执行 <br>
	 *  false : 程序中断执行,读取结束
	 */
	protected boolean onFileSizeExceed() throws Exception{
		return false;
	}
	
	/**
	 * 文件上传读取完全结束并且没有发生错误时调用该方法[可选项]<br>
	 * @throws IOException 
	 */
	protected void onUploadFinish() throws Exception{ }
	/**
	 * 上传预处理,上传之前调用[可选项]<br><br>
	 * @return
	 * 	true ： 上传继续执行<br>
	 * 	false ：程序中断执行,读取结束
	 */
	protected boolean preWorks() throws Exception{
		return true;
	}
	/**
	 * 开始处理上传的过程
	 * @throws IOException
	 */
	public void beginUpload() throws Exception{
		initConfig();
		if(!preWorks()){
			return;
		}
		long total = request.getContentLength();
		if(total > maxUploadSize || total <= 0){//文件超过限制
			if(!onFileSizeExceed()){
				if(null != ops)
					ops.close();
				return;
			}
		}
		String contentType = request.getContentType();
		String agent = "";
		Enumeration<String> hs = request.getHeaderNames();
		while(hs.hasMoreElements()){
			String n = hs.nextElement();
			if(n.contains("agent"))
				agent = request.getHeader(n);
			//System.out.println(n + "=" + request.getHeader(n));
		}
		if(contentType.matches("multipart/form-data; boundary=.*")){
			this.boundary = contentType.replaceAll(".*boundary=(.*)", "$1");
			
			String paraSeparator = "--" + boundary;
			String endSeparator = "--" + boundary + "--";
			
			//修正，缓冲区不能太小
			if(buffersize < 1024) {
				buffersize = 1024;
			}
			paraMap = new HashMap<String, String>();
			boolean readname = false;
			boolean readStrValue = false;
			boolean readByteValue = false;
			boolean skipToFile = false;
			boolean skipToValue = false;
			
			boolean endRead = false;
			
			String paraName = null;
			String paraValue = null;
			boolean begin = false;
			
			InputStream ips = request.getInputStream();
			byte tmp[] = new byte[1];
			byte[] move = new byte[2];
			byte b;
			ByteBuffer bf = ByteBuffer.allocate(buffersize);
			int bf_pos = 0;
			try {

				String sb = null;
				while(ips.read(tmp) != -1){
					if(bf_pos == buffersize){
						if(readStrValue){
							if(null == paraValue)
								paraValue = new String(bf.array(), 0, bf_pos-1, "utf-8");
							else
								paraValue += new String(bf.array(), 0, bf_pos-1, "utf-8");
							bf.clear();
							bf_pos=0;
						}
						else{
							if(begin){
								writeFile(new byte[]{13, 10});
								begin = false;
							}
							writeFile(bf.array());
							bf.clear();
							bf_pos=0;
						}
					}
					
					b = tmp[0];
					bf.put(b);
					bf_pos++;
					if(b == 13){
						move[0] = b;
						if(ips.read(tmp) != -1){
							b = tmp[0];
							move[1] = b;
							if(b == 10){
								sb = new String(bf.array(), 0, bf_pos-1, "utf-8");
								//System.out.println(sb);
								if(sb.equals(endSeparator)){
									if(readStrValue){
										paraMap.put(paraName, paraValue);
										if(!onTextField(paraName, paraValue)){
											if(null != ops)
												ops.close();
											return;
										}
										paraValue = null;
									}
									if(readByteValue){
										if(null != ops)
											ops.close();
										//System.out.println("-----1");
										onFileEnd();
									}
									endRead = true;
									break;
								}
								if(sb.equals(paraSeparator)){
									if(readStrValue){
										paraMap.put(paraName, paraValue);
										if(!onTextField(paraName, paraValue)){
											if(null != ops)
												ops.close();
											return;
										}
										paraValue = null;
									}
									if(readByteValue){
										if(null != ops)
											ops.close();
										//System.out.println("-----2");
										onFileEnd();
									}
									readname = true;
									readStrValue = false;
									readByteValue = false;
									skipToFile = false;
									skipToValue = false;
									bf.clear();
									bf_pos=0;
									continue;
								}
								
								if(readStrValue) {
									if(null == paraValue)
										paraValue = sb;
									else
										paraValue += sb;
									bf.clear();
									bf_pos=0;
									continue;
								}
								if(readByteValue){
									if(begin){
										writeFile(new byte[]{13, 10});
									}
									writeFile(bf.array(), 0, bf_pos-1);
									begin = true;
									bf.clear();
									bf_pos=0;
									continue;
								}
								if(skipToValue){
									readname = false;
									readStrValue = true;
									readByteValue = false;
									skipToFile = false;
									skipToValue = false;
									bf.clear();
									bf_pos=0;
									continue;
								}
								if(skipToFile){
									readname = false;
									readStrValue = false;
									readByteValue = true;
									skipToFile = false;
									skipToValue = false;
									ips.skip(2);
									bf.clear();
									bf_pos=0;
									begin = false;
									continue;
								}
								if(readname){
									if(sb.matches(".*filename=\".*\"")){
										paraName = sb.replaceAll(".*filename=\"(.*)\"", "$1");
										if(paraName.contains("\\"))
											paraName = paraName.substring(paraName.lastIndexOf("\\") + 1);
										filename = paraName;
										if(!onFileField(paraName))
											break;
										readname = false;
										readStrValue = false;
										readByteValue = false;
										skipToFile = true;
										skipToValue = false;
									}else{
										paraName = sb.replaceAll(".*name=\"(.*)\"", "$1");
										paraName = new String(paraName.getBytes());
										readname = false;
										readStrValue = false;
										readByteValue = false;
										skipToFile = false;
										skipToValue = true;
									}
									bf.clear();
									bf_pos=0;
									continue;
								}
							}else{
								if(bf_pos == buffersize){
									if(readStrValue){
										if(null == paraValue)
											paraValue = new String(bf.array(), 0, bf_pos-1, "utf-8");
										else
											paraValue += new String(bf.array(), 0, bf_pos-1, "utf-8");
										bf.clear();
										bf_pos=0;
									}
									else{
										if(begin){
											writeFile(new byte[]{13, 10});
											begin = false;
										}
										writeFile(bf.array());
										bf.clear();
										bf_pos=0;
									}
								}
								b = tmp[0];
								bf.put(b);
								bf_pos++;
							}
						}else{
							endRead = true;
							break;
						}
					}
				}
				if(agent.contains("Shockwave") || agent.contains("Flash")){
					onTextField(paraName, paraValue);
				}
				endRead = true;
			} catch (Exception e) {
				e.printStackTrace();
				if(null != ops)
					ops.close();
				if(!onError(e)){
					return;
				}
			}
			
			if(!endRead){
				if(null != ops)
					ops.close();
				onRequestInputStreamInterrupt();
			}else{
				if(null != ops)
					ops.close();
				onUploadFinish();
			}
		}
	}
	

	public HttpServletRequest getRequest() {
		return request;
	}
	public void setRequest(HttpServletRequest request) {
		this.request = request;
	}
	public HttpServletResponse getResponse() {
		return response;
	}
	public void setResponse(HttpServletResponse response) {
		this.response = response;
	}
	
}
