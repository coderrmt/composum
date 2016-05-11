<%@page session="false" pageEncoding="utf-8"%>
<%@taglib prefix="sling" uri="http://sling.apache.org/taglibs/sling/1.2"%>
<%@taglib prefix="cpn" uri="http://sling.composum.com/cpnl/1.0"%>
<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<sling:defineObjects/>
<cpn:component id="browser" type="com.composum.sling.core.browser.Browser" scope="request">
  <div id="pckg-upload-dialog" class="dialog modal fade" role="dialog" aria-hidden="true">
    <div class="modal-dialog">
      <div class="modal-content form-panel default">

        <cpn:form classes="widget-form" enctype="multipart/form-data" action="/bin/core/package.upload.json">

          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-label="Close"><span
                aria-hidden="true">&times;</span></button>
            <h4 class="modal-title">Upload Package</h4>
          </div>
          <div class="modal-body">
            <div class="messages">
              <div class="alert"></div>
            </div>
            <input name="_charset_" type="hidden" value="UTF-8" />

            <div class="form-group">
              <label class="control-label">Package File</label>
              <input name="file" class="widget file-upload-widget form-control" type="file"
                     data-options="hidePreview"/>
            </div>
            <div class="form-group">
              <label class="control-label">Force Upload</label>
              <input name="force" class="widget checkbox-widget form-control" type="checkbox"/>
            </div>
          </div>

          <div class="modal-footer buttons">
            <button type="button" class="btn btn-default cancel" data-dismiss="modal">Cancel</button>
            <button type="submit" class="btn btn-primary upload">Upload</button>
          </div>
        </cpn:form>
      </div>
    </div>
  </div>
</cpn:component>