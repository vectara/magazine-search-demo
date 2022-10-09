const QueryType  = {
    ALL: 0
}

document.vectara = {}
document.vectara.num_results = 10;
document.vectara.num_related_results = 5;
document.vectara.prev_query = "";  // The last successful query.
document.vectara.query_type = QueryType.ALL;
document.vectara.query_type_dict = {};
document.vectara.related_articles = false;

Date.prototype.military = function() {
    var shortMonths = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var mm = this.getMonth(); // getMonth() is zero-based
    var dd = this.getDate();
  
    return [this.getFullYear(),
            shortMonths[mm],
            (dd>9 ? '' : '0') + dd
           ].join(' ');
};
  
Date.prototype.short = function() {
    var shortMonths = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    var mm = this.getMonth(); // getMonth() is zero-based
    var dd = this.getDate();
  
    return [shortMonths[mm],
            dd +",", this.getFullYear()
           ].join(' ');
};
  
function preloadImage(url) {
  var img=new Image();
  img.src=url;
}
preloadImage("img/logo.png");
preloadImage("img/expand.svg");
preloadImage("img/collapse.svg");

$(document).ready(function () {
    $('#num-results').val(10);
    $('#search-form').on('submit', function(e) {
        e.preventDefault();
        search();
    });
  
    $(".logo").click(function () {
      search();
      return false;
    });

    document.vectara.query_type_dict[QueryType.ALL] = $("div[data-query-type='0']");
    document.vectara.query_type_dict[QueryType.ARTICLES] = $("div[data-query-type='1']");
    document.vectara.query_type_dict[QueryType.DRUGS] = $("div[data-query-type='2']");
});

/**
 * Extract the query from the history.state
 */
function extractQuery(state) {
    if (state === null) {
        if (window.location.hash) {
            return decodeURIComponent(window.location.hash.substring(1));
        } else {
            return null;
        }
    }
    if (state.query) {
        return state.query;
    }
}

/**
 * Extract the query from the history.state and initiate a search.
 */
function restoreState(state) {
    if (state === null) {
        if (window.location.hash) {
            let query = decodeURIComponent(window.location.hash.substring(1));
            document.getElementById('search-input').value = query;
            search();
        } else {
            resetPage();
        }
        return;
    }
    if (state.query) {
        document.getElementById('search-input').value = state.query;
        search();
    }
}
window.onpopstate = (e) => restoreState(e.state);

function search() {
    let query = document.getElementById('search-input').value;
    if (query === "" || query === document.vectara.prev_query) {
      console.log("suppressing duplicate or empty query")
      return;
    }
    history.pushState({'query': query}, '', `#${query}`);
    clearSearchResults();
    $("#search-form .logo").addClass("loading");
    search_with_token(query);
}

function search_with_token(query) {
    let relatedButton = '';
    var template = `
      {{#docs}}
      <li class="result">
        <div class="attribution">
          <div class="authority">{{authority}}</div>
          <div class="crumbs">
            <span class="breadcrumb-sep">›</span>
            <span class="breadcrumb">{{{siteCategory}}}</span>
          </div>  
        </div>
        <h2><a href="{{page-url}}">{{title}}</a></h2>
        <snippet>
        <span class="date">{{{formatted-date}}}</span>
        <span class="crumbs">{{{rendered-breadcrumb}}}</span>
        {{{body-snippet}}}
        ${relatedButton}
        </snippet>
      </li>
      {{/docs}}`;
    
    $.ajax({
        url : '/query',
        type : 'POST',
        data : {
            'q' : query,
            'n' : document.vectara.num_results,
            't' : document.vectara.query_type
        },
        dataType:'json',
        start_time: new Date().getTime(),      // https://stackoverflow.com/questions/3498503
        success : function(data) { 
            document.vectara.prev_query = query;    // save the last successful query.
            document.vectara.result_set = data;
            $("#search-form .logo").removeClass("loading");
            $('#search-results').removeClass("error");
            
            if (data.docs.length == 0 && data.commonQuestions.length == 0 && !data.panel  && !data.drugPanel) {
              $('#no-results').show();
              return;
            } else {
              $('#no-results').hide();
            }
            
            if (data.sidePanel) {
              $('#sidebar').html(data.sidePanel.content);
              $('#sidebar').show();
            } else {
              $('#sidebar').hide();
            }
            
            if (data.panel) {
              $('#snippet-content').html(data.panel.content);
              let d = new Date(0);
              d.setUTCSeconds(data.panel.retrievalDate);
              $('#snippet-retrieved').html(d.short());
              $('#snippet-source').html(`<a href="${data.panel.authorityUrl}">${data.panel.authority}</a>`);
              $('#featured-snippet').show();
            } else {
              $('#featured-snippet').hide();
            }
            
            if (data.commonQuestions && data.commonQuestions.length > 0) {
              renderCommonQuestions(data);
              $('#common-questions').show();
            } else {
              $('#common-questions').hide();
            }
            
            let idx = 0;
            data["idx"] = function() {
              return idx++;
            }
            data["page-url"] = function () {
              var doc = this;
              if (doc.textFragment && doc.textFragment !== "") {
                return `${doc.url}${doc.textFragment}`
              } else {
                return doc.url;
              }
            }
            data["formatted-date"] = function () {
              var doc = this;              
              let tooltip = "published date";
              let d = new Date(0);
              d.setUTCSeconds(doc.date);
              return `<span title="${tooltip}">${d.short()} – </span>`;
            };
            data["rendered-breadcrumb"] = function () {
              var doc = this;
              var pieces = []
              for (var i = 0; i < doc.breadcrumb.length; i++) {
                let crumb = doc.breadcrumb[i];
                pieces.push(`<span class="breadcrumb">${crumb.display}</span>`)
                pieces.push('<span class="breadcrumb-sep">›</span>');
              }
              return pieces.join('');
            };
            data["body-snippet"] = function () {
              var doc = this;
              if (doc.sections.length < 1) {
                return "";
              }
              let section = doc.sections[0];
              if (section.pre === "" && section.post === "") {
                return section.text;
              } else {
                return `${section.pre}<em>${section.text}</em>${section.post}`;
              }
            };
            var html = Mustache.render(template, data);
            $('#search-results').html(html);
            setLatency(new Date().getTime() - this.start_time);
            if (window.innerWidth <= 780) {
              window.scrollBy({top: 52, behavior: 'smooth'});
            }
        },
        error : function(request, error)
        {
            $("#search-form .logo").removeClass("loading");
            let message = request.responseText.match(/message: \"(.*)\"/);
            
            console.log(request.responseText);
            console.log(message);
            
            $('#search-results').addClass("error");
            let html = `<li class="error">Oops! Something went wrong. Please try your request again later.</li>`;
            if (message.length >= 2) {
                html = `
                <li class="error">
                    <div>Oops! Something went wrong. Please try your request again later.</div>
                    <div style='margin: 6px 0 0 8px; color: #cc5500;'>⮡<span style='margin-left: 8px;'>Server details: ${message[1]}</span></div>
                </li>`
            }
            $('#search-results').html(html);
            Prism.highlightAllUnder($("#search-results")[0], false, null);
            setLatency(new Date().getTime() - this.start_time);
        }
    });
}

function setLatency(timeMs) {
  $("latency").html(timeMs.toFixed(2) + " ms");
}

function clearQueryBox() {
  $("#search-input").val("");
}

/**
 * Empty the search results area.
 */
function clearSearchResults() {
  $('#search-results').html("");
  $('#welcome').hide();
  $('#featured-snippet').hide();
  $('#common-questions').hide();
  $('#related-articles').hide();
  $('#sidebar').hide();
}

/**
 * Reset the page to its initial state
 */
function resetPage() {
  $('#search-results').html("");
  $('#featured-snippet').hide();
  $('#common-questions').hide();
  $('#related-articles').hide();
  $('#sidebar').hide();
  $('#welcome').show();
}

/**
 * Invoked when the user changes the number of display results.
 */
function numResultsChanged() {
  document.vectara.num_results = $("#num-results").val();
  $("num-search-results").html(document.vectara.num_results);
  document.vectara.prev_query = "";  // to allow the search request to succeed.
  search();
}

function runSampleQuery(textSpan) {
  $("#search-input").val(textSpan.innerHTML);
  search();
}

/**
 * Invoked when a user toggles question display.
 */
function toggleQuestion(questionBlock) {
  let expanded = questionBlock.getAttribute('expanded');
  let img = $(questionBlock).find('img');
  if (expanded == 'true') {
    img.attr('src', 'img/expand.svg');
    let answer = $(questionBlock).find('.answer');
    answer.hide();
    questionBlock.setAttribute('expanded', 'false');
  } else {
    img.attr('src', 'img/collapse.svg');
    let answer = $(questionBlock).find('.answer');
    answer.show();
    questionBlock.setAttribute('expanded', 'true');
  }
}

/**
 * Show commonly-asked questions.
 */
function renderCommonQuestions(data) {
    var template = `
      {{#commonQuestions}}
        <div class="qa-block" expanded="false" onclick="toggleQuestion(this);">
          <div class="question">
            <div>{{question}}</div>
            <img src="img/expand.svg">
          </div>
          <div class="answer">
            {{{answer}}}
            <div class="attribution"><span class="authority">[{{authority}}]</span> <a href="{{sourceUrl}}">{{sourceTitle}}</a></div>
          </div>
        </div>
      {{/commonQuestions}}`;
    var html = Mustache.render(template, data);
    $('#question-content').html(html);
}

//document.vectara.query_type = QueryType.ALL;
function setQueryType(tabControl) {
  queryType = parseInt(tabControl.dataset.queryType);
  if (queryType == document.vectara.query_type) {
    // ignore the request
    return;
  }
  document.vectara.query_type_dict[document.vectara.query_type].removeClass("selected");
  document.vectara.query_type_dict[queryType].addClass("selected");
  document.vectara.query_type = queryType;

  document.vectara.prev_query = "";  // to allow the search request to succeed.
  search();

}
