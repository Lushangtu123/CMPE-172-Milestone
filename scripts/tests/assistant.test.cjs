const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../../src/main/resources/static/assets/assistant.js'), 'utf8');
class Element {
  constructor(){this.textContent='';this.value='';this.hidden=true;this.disabled=false;this.events={};this.children=[];this.dataset={};}
  addEventListener(name,fn){this.events[name]=fn;}
  setAttribute(name,value){this[name]=value;}
  querySelector(){return {value:'csrf-test'};}
  replaceChildren(){this.children=[];}
  append(...children){this.children.push(...children);for(const child of children)child.parent=this;}
  get childElementCount(){return this.children.length;}
  remove(){this.parent.children=this.parent.children.filter(child=>child!==this);}
}
async function fixture(){
 const names=['chat-form','agent-error','agent-status','conversation','chat-content','proposal','proposal-summary','proposal-title','proposal-details','proposal-service','proposal-tutor','proposal-time','message','send','confirm','discard','reset','assistant-welcome'];
 const elements=Object.fromEntries(names.map(name=>[name,new Element()]));
 const timers=new Map();let nextTimer=0;const requests=[];
 let reply;
 const context={AbortController,DOMException,URLSearchParams,Event,
  setTimeout(fn,delay){const id=++nextTimer;timers.set(id,{fn,delay});return id;},
  clearTimeout(id){timers.delete(id);},
  document:{dispatchEvent(){},getElementById:id=>elements[id],querySelectorAll:selector=>selector==='main button'
    ? ['send','confirm','discard','reset'].map(n=>elements[n]) : [],createElement:()=>new Element()},
  fetch(url,options){requests.push({url,options});
   if(url.endsWith('/history'))return Promise.resolve({ok:true,json:async()=>({messages:[],pending:null})});
   return new Promise((resolve,reject)=>{
     reply=data=>resolve({ok:true,json:async()=>data});
     if(options.signal)options.signal.addEventListener('abort',()=>reject(new DOMException('Aborted','AbortError')));
   });
  }
 };
 vm.runInNewContext(script,context);await new Promise(setImmediate);
 return {elements,timers,requests,reply:data=>reply(data)};
}
test('chat shows progress while waiting for the model',async()=>{
 const {elements}=await fixture();elements.message.value='What is the cancellation policy?';
 elements['chat-form'].events.submit({preventDefault(){}});await new Promise(setImmediate);
 assert.match(elements.send.textContent,/Thinking|Sending/i);
 assert.equal(elements.send.disabled,true);
 assert.match(elements['agent-status'].textContent,/AI|wait|policy|checking/i);
 assert.equal(elements.conversation.children[0].children[1].textContent,'What is the cancellation policy?');
 assert.equal(elements['assistant-welcome'].hidden,true);
});
test('a successful reply replaces the preview with one copy of the submitted message',async()=>{
 const {elements,reply}=await fixture();
 elements.message.value='Hello';
 elements['chat-form'].events.submit({preventDefault(){}});
 reply({messages:[{role:'user',text:'Hello'},{role:'assistant',text:'How can I help?'}],pending:null});
 await new Promise(setImmediate);
 assert.deepEqual(elements.conversation.children.map(turn=>turn.children[1].textContent),
   ['Hello','How can I help?']);
 assert.equal(elements.message.value,'');
 assert.equal(elements['agent-status'].textContent,'');
});
test('confirmation renders the returned conversation without reloading history',async()=>{
 const {elements,reply,requests}=await fixture();
 elements.message.value='Book algebra';
 elements['chat-form'].events.submit({preventDefault(){}});
 const turns=[{role:'user',text:'Book algebra'},{role:'assistant',text:'Confirm algebra?'}];
 reply({messages:turns,pending:{token:'proposal-token',summary:'Algebra'}});
 await new Promise(setImmediate);
 elements.confirm.events.click();
 const message='Booking confirmed. Appointment #5.';
 reply({appointmentId:5,message,messages:[...turns,{role:'assistant',text:message}],pending:null});
 await new Promise(setImmediate);
 assert.equal(elements.conversation.children.at(-1).children[1].textContent,message);
 assert.equal(elements.proposal.hidden,true);
 assert.equal(requests.filter(request=>request.url.endsWith('/history')).length,1);
});
test('stalled chat times out, unlocks controls and does not wait on locked history',async()=>{
 const {elements,timers,requests}=await fixture();elements.message.value='Hello';elements.proposal.hidden=false;
 elements['chat-form'].events.submit({preventDefault(){}});await new Promise(setImmediate);
 const timeout=[...timers.values()].find(timer=>timer.delay===45000);
 assert.ok(timeout,'chat must have a 45 second deadline');timeout.fn();await new Promise(setImmediate);await new Promise(setImmediate);
 assert.equal(elements.send.disabled,false);
 assert.match(elements['agent-error'].textContent,/taking too long|timed out/i);
 assert.equal(elements.proposal.hidden,true);
 assert.equal(elements.message.value,'Hello');
 assert.equal(elements.conversation.children.length,0);
 assert.equal(requests.filter(r=>r.url.endsWith('/history')).length,1,'only initial history request; timeout must not wait on history held by in-flight chat');
});
