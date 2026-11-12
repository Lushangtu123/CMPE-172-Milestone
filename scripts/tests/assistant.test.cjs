const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/assets/assistant.js'), 'utf8');
class Element {
  constructor(){this.textContent='';this.value='';this.hidden=true;this.disabled=false;this.events={};this.children=[];}
  addEventListener(name,fn){this.events[name]=fn;}
  setAttribute(name,value){this[name]=value;}
  querySelector(){return {value:'csrf-test'};}
  replaceChildren(){this.children=[];}
  append(...children){this.children.push(...children);}
}
async function fixture(){
 const names=['chat-form','agent-error','agent-status','conversation','proposal','proposal-summary','message','send','confirm','discard','reset'];
 const elements=Object.fromEntries(names.map(name=>[name,new Element()]));
 const timers=new Map();let nextTimer=0;const requests=[];
 const context={AbortController,DOMException,URLSearchParams,
  setTimeout(fn,delay){const id=++nextTimer;timers.set(id,{fn,delay});return id;},
  clearTimeout(id){timers.delete(id);},
  document:{getElementById:id=>elements[id],querySelectorAll:()=>['send','confirm','discard','reset'].map(n=>elements[n]),createElement:()=>new Element()},
  fetch(url,options){requests.push({url,options});
   if(url.endsWith('/history'))return Promise.resolve({ok:true,json:async()=>({messages:[],pending:null})});
   return new Promise((resolve,reject)=>{if(options.signal)options.signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')));});
  }
 };
 vm.runInNewContext(script,context);await new Promise(setImmediate);
 return {elements,timers,requests};
}
test('chat shows progress while waiting for the model',async()=>{
 const {elements}=await fixture();elements.message.value='What is the cancellation policy?';
 elements['chat-form'].events.submit({preventDefault(){}});await new Promise(setImmediate);
 assert.match(elements.send.textContent,/Thinking|Sending/i);
 assert.equal(elements.send.disabled,true);
 assert.match(elements['agent-status'].textContent,/AI|wait|policy|checking/i);
});
test('stalled chat times out, unlocks controls and does not wait on locked history',async()=>{
 const {elements,timers,requests}=await fixture();elements.message.value='Hello';elements.proposal.hidden=false;
 elements['chat-form'].events.submit({preventDefault(){}});await new Promise(setImmediate);
 const timeout=[...timers.values()].find(timer=>timer.delay===45000);
 assert.ok(timeout,'chat must have a 45 second deadline');timeout.fn();await new Promise(setImmediate);await new Promise(setImmediate);
 assert.equal(elements.send.disabled,false);
 assert.match(elements['agent-error'].textContent,/taking too long|timed out/i);
 assert.equal(elements.proposal.hidden,true);
 assert.equal(requests.filter(r=>r.url.endsWith('/history')).length,1,'only initial history request; timeout must not wait on history held by in-flight chat');
});
