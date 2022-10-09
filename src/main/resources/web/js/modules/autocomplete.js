/**
 * Autocompletion support. (C) 2020, ZIR AI, Inc.
 */

class Complete { 
  suggestBox;
  constructor(textbox_selector) {
    this.suggestBox = document.createElement('DIV');
    this.suggestBox.innerHTML = 'How do you do today?';
    
    
    // https://stackoverflow.com/questions/43727516/javascript-how-adding-event-handler-inside-a-class-with-a-class-method-as-the-c/43727582
    $(textbox_selector).keyup(this.onKeyup.bind(this));
  }
  
  onKeyup(event) {
    let target = event.target;
    let rect = target.getBoundingClientRect();
    
    let left = rect.left;
    let top = rect.bottom;

    this.suggestBox.setAttribute('style', 'background: #B0B0B0; padding: 6px; position: absolute; top: ' + top + 'px; left: ' + left + 'px; display: none;');
    target.parentNode.appendChild(this.suggestBox);
    //this.suggestBox.style.display = 'block';
  }
};

export {Complete};
