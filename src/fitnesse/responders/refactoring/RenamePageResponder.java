// Copyright (C) 2003-2009 by Object Mentor, Inc. All rights reserved.
// Released under the terms of the CPL Common Public License version 1.0.
package fitnesse.responders.refactoring;

import fitnesse.FitNesseContext;
import fitnesse.Responder;
import fitnesse.authentication.AlwaysSecureOperation;
import fitnesse.authentication.SecureOperation;
import fitnesse.authentication.SecureResponder;
import fitnesse.components.PageReferenceRenamer;
import fitnesse.html.HtmlUtil;
import fitnesse.http.Request;
import fitnesse.http.Response;
import fitnesse.http.SimpleResponse;
import fitnesse.responders.ErrorResponder;
import fitnesse.responders.NotFoundResponder;
import fitnesse.wiki.PageCrawler;
import fitnesse.wiki.PageData;
import fitnesse.wiki.PathParser;
import fitnesse.wiki.WikiPage;
import fitnesse.wiki.WikiPagePath;
import fitnesse.wikitext.widgets.WikiWordWidget;

public class RenamePageResponder implements SecureResponder {
  private String qualifiedName;
  private String newName;
  private boolean refactorReferences;
  private WikiPagePath pathToRename;
  private WikiPage pageToRename;
  private WikiPage root;
  private WikiPage parentOfPageToRename;

  public Response makeResponse(FitNesseContext context, Request request) throws Exception {
    root = context.root;
    qualifiedName = request.getResource();
    newName = (String) request.getInput("newName");
    refactorReferences = request.hasInput("refactorReferences");

    if (newName == null || !WikiWordWidget.isSingleWikiWord(newName) || "FrontPage".equals(qualifiedName)) {
      return makeErrorMessageResponder(newName + " is not a valid simple page name.").makeResponse(context, request);
    }

    PageCrawler pageCrawler = context.root.getPageCrawler();

    pathToRename = PathParser.parse(qualifiedName);
    pageToRename = pageCrawler.getPage(context.root, pathToRename);
    if (pageToRename == null)
      return new NotFoundResponder().makeResponse(context, request);

    WikiPagePath parentPath = pathToRename.parentPath();
    parentOfPageToRename = pageCrawler.getPage(context.root, parentPath);
    final boolean pageExists = pageCrawler.pageExists(parentOfPageToRename, PathParser.parse(newName));
    if (pageExists) {
      return makeErrorMessageResponder(makeLink(newName) + " already exists").makeResponse(context, request);
    }

    qualifiedName = renamePageAndMaybeAllReferences();
    Response response = new SimpleResponse();
    response.redirect(qualifiedName);

    return response;
  }

  private Responder makeErrorMessageResponder(String message) throws Exception {
    return new ErrorResponder("Cannot rename " + makeLink(qualifiedName) + " to " + newName + "<br/>" + message);
  }

  private String makeLink(String page) throws Exception {
    return HtmlUtil.makeLink(page, page).html();
  }

  private String renamePageAndMaybeAllReferences() throws Exception {
    if (refactorReferences)
      renameReferences();
    renamePage();

    pathToRename.removeNameFromEnd();
    pathToRename.addNameToEnd(newName);
    return PathParser.render(pathToRename);
  }

  private void renameReferences() throws Exception {
    PageReferenceRenamer renamer = new PageReferenceRenamer(root);
    renamer.renameReferences(pageToRename, newName);
  }

  private boolean renamePage() throws Exception {
    String oldName = pageToRename.getName();
    if (!parentOfPageToRename.hasChildPage(oldName) || parentOfPageToRename.hasChildPage(newName)) {
      return false;
    }

    WikiPage originalPage = parentOfPageToRename.getChildPage(oldName);
    PageCrawler crawler = originalPage.getPageCrawler();
    PageData data = originalPage.getData();

    WikiPage renamedPage = parentOfPageToRename.addChildPage(newName);
    renamedPage.commit(data);

    for (WikiPage child: originalPage.getChildren()) {
      MovePageResponder.movePage(root, crawler.getFullPath(child), crawler.getFullPath(renamedPage));
    }

    parentOfPageToRename.removeChildPage(oldName);
    return true;
  }

  public SecureOperation getSecureOperation() {
    return new AlwaysSecureOperation();
  }
}
