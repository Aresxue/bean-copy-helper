package cn.ares.bean.copy.helper;

import com.intellij.openapi.diagnostic.ErrorReportSubmitter;
import com.intellij.openapi.diagnostic.IdeaLoggingEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diagnostic.SubmittedReportInfo;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.util.Consumer;
import java.awt.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Ares
 * @time: 2025-09-29 10:54:22
 * @description: 全局异常处理
 * @description: Global error handler
 * @version: JDK 1.8
 */
public class GlobalErrorHandler extends ErrorReportSubmitter {

  private static final Logger LOGGER = Logger.getInstance(GlobalErrorHandler.class);

  @Override
  public @ActionText @NotNull String getReportActionText() {
    return "Report to BeanCopyHelper";
  }

  @Override
  public boolean submit(IdeaLoggingEvent @NotNull [] events, @Nullable String additionalInfo,
      @NotNull Component parentComponent, @NotNull Consumer<? super SubmittedReportInfo> consumer) {
    // 处理未捕获的异常
    // Handle uncaught exception
    for (IdeaLoggingEvent event : events) {
      LOGGER.error("Uncaught exception", event.getThrowable());
    }
    return true;
  }

}
