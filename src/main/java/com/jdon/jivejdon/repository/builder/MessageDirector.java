/*
 * Copyright 2007 the original author or jdon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.jdon.jivejdon.repository.builder;

import com.jdon.jivejdon.model.*;
import com.jdon.jivejdon.model.message.AnemicMessageDTO;
import com.jdon.jivejdon.model.message.FilterPipleSpec;
import com.jdon.jivejdon.model.message.MessageVO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builder pattern for ForumMessage
 */
public class MessageDirector {
	private final static Logger logger = LogManager.getLogger(MessageDirector.class);

	private final MessageBuilder messageBuilder;

	private final Map nullmessages;


	public MessageDirector(MessageBuilder messageBuilder) {
		super();
		this.messageBuilder = messageBuilder;
		this.nullmessages = lruCache(100);
	}

	public static <K, V> Map<K, V> lruCache(final int maxSize) {
		return new LinkedHashMap<K, V>(maxSize * 4 / 3, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
				return size() > maxSize;
			}
		};
	}

	public ForumMessage getMessageWithPropterty(Long messageId) {
		return buildMessage(messageId);
	}

//	public ForumMessage getMessage(Long messageId) {
//		if (messageId == null || messageId == 0)
//			return null;
//		try {
//			return getMessage(messageId, null, null);
//		} catch (Exception e) {
//			return null;
//		}
//	}

	public ForumMessage buildMessage(Long messageId) {
		if (messageId == null || messageId == 0)
			return null;
		try {
			return buildMessage(messageId, null, null);
		} catch (Exception e) {
			return null;
		}
	}


	public MessageVO getMessageVO(ForumMessage forumMessage) {
		if (forumMessage.getMessageId() == null || forumMessage.getMessageId() == 0)
			return null;
		MessageVO mVO = null;
		try {
			mVO = messageBuilder.createMessageVO(forumMessage);
			// if construts mVo put code here
		} catch (Exception e) {
			return null;
		}
		return mVO;
	}

	/*
	 * builder pattern with lambdas
	 * return a full ForumMessage need solve the relations with Forum
	 * ForumThread parentMessage
	 */
	public ForumMessage buildMessage(Long messageId, ForumThread forumThread, Forum
			forum) throws Exception {
		if (messageId == null || messageId == 0) {
			return null;
		}
		logger.debug(" enter createMessage for id=" + messageId);
		if (nullmessages.containsKey(messageId)) {
			logger.error("repeat no this message in database id=" + messageId);
			return null;
		}
		ForumMessage forumMessage = this.messageBuilder.messageDao.getForumMessageInjection(messageId);
		if (forumMessage.isSolid())//if from cache , if being solid
			return forumMessage;

		try {
			final AnemicMessageDTO anemicMessageDTO = (AnemicMessageDTO) messageBuilder.createAnemicMessage(messageId);
			if (anemicMessageDTO == null) {
				nullmessages.put(messageId, "NULL");
				logger.error("no this message in database id=" + messageId);
				return null;
			}
			ForumMessage parentforumMessage = null;
			if (anemicMessageDTO.getParentMessage() != null && anemicMessageDTO.getParentMessage().getMessageId() != null) {
				parentforumMessage = buildMessage(anemicMessageDTO.getParentMessage().getMessageId(), forumThread, forum);
			}

			Optional<Account> accountOptional = messageBuilder.createAccount(anemicMessageDTO.getAccount());
			FilterPipleSpec filterPipleSpec = new FilterPipleSpec(messageBuilder.getOutFilterManager().getOutFilters());
			if ((forum == null) || (forum.lazyLoaderRole == null) || (forum.getForumId().longValue() != anemicMessageDTO.getForum().getForumId().longValue())) {
				forum = messageBuilder.getForumAbstractFactory().forumDirector.getForum(anemicMessageDTO.getForum().getForumId());
			}
			Long threadId = anemicMessageDTO.getForumThread().getThreadId();
			if ((forumThread == null) || (forumThread.lazyLoaderRole == null) || (threadId.longValue() != forumThread.getThreadId().longValue())) {
				forumThread = messageBuilder.getForumAbstractFactory().threadDirector.getThread(threadId, parentforumMessage != null ? null : forumMessage, forum);
			}
			forumMessage.messageBuilder().messageId(anemicMessageDTO.getMessageId()).messageVO
					(anemicMessageDTO.getMessageVO()).forum
					(forum).forumThread(forumThread)
					.acount(accountOptional.orElse(new Account())).creationDate(anemicMessageDTO.getCreationDate()).modifiedDate(anemicMessageDTO.getModifiedDate()).filterPipleSpec(filterPipleSpec)
					.uploads(null).props(null).build(forumMessage, parentforumMessage);
		}catch (Exception e){
			logger.error("buildMessage exception "+ e.getMessage() + " messageId=" + messageId);
		}
		return forumMessage;

	}



}
