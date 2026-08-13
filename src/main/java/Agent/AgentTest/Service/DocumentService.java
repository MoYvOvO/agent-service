package Agent.AgentTest.Service;

import org.apache.tika.Tika;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
public class DocumentService {

    @Autowired
    private VectorStore vectorStore;
    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    @PostConstruct
    public void loadDocuments() {
        try {
            File dir = ResourceUtils.getFile("classpath:documents");
            if (!dir.exists()) {
               // System.out.println("📁 documents 文件夹不存在，跳过加载");
                log.info("📁 documents 文件夹不存在，跳过加载");

                return;
            }

            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
               // System.out.println("📁 documents 文件夹为空，跳过加载");
                log.info("📁 documents 文件夹为空，跳过加载");
                return;
            }

            List<Document> documentList = new ArrayList<>();

            for (File file : files) {
                String content = "";
               // System.out.println("📄 正在读取: " + file.getName());
                log.info("📄 正在读取: {}",file.getName());

                try {
                    // 对 .txt 文件直接用 Files 读取，其他用 Tika
                    if (file.getName().endsWith(".txt")) {
                        content = new String(Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                    } else {
                        Tika tika = new Tika();
                        try (FileInputStream fis = new FileInputStream(file)) {
                            content = tika.parseToString(fis);
                        }
                    }
                } catch (Exception e) {
                    //System.err.println("解析文件失败: " + file.getName() + " - " + e.getMessage());
                    log.error("解析文件失败: {} - {}", file.getName(), e.getMessage(), e);
                    continue;
                }

                if (!content.isBlank()) {
                    Document springDoc = new Document(content);
                    springDoc.getMetadata().put("source", file.getName());
                    documentList.add(springDoc);
                   // System.out.println("   ✅ 已读取 " + content.length() + " 个字符");
                    log.info("   ✅ 已读取{}个字符",content.length());
                }
            }

            if (!documentList.isEmpty()) {
                vectorStore.add(documentList);
                //System.out.println("✅ 第二阶段成功！共加载并向量化 " + documentList.size() + " 个文档");
                log.info("✅ 第二阶段成功！共加载并向量化{}个文档 ",documentList.size());
            }

        } catch (Exception e) {
            //System.err.println("❌ 文档加载失败: " + e.getMessage());
            log.error("❌ 文档加载失败: {}",e.getMessage(),e);
            e.printStackTrace();
        }
    }
}