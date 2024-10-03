package de.lemke.oneurl.domain.model

/*
https://dub.co/
https://dub.co/docs/api-reference
requires api key
but:
https://dub.co/api/links {"url": "example.com", "key": "example123", "publicStats": true}
header: dub-anonymous-link-creation 1

but: To prevent abuse, we automatically delete unclaimed links after 30 minutes. To claim this link, simply sign up for a free account. :/

response:
{
  "id": "cm1tsr6eh0008mbug20nsquct",
  "domain": "dub.sh",
  "key": "example123",
  "url": "https://example.com/",
  "archived": false,
  "expiresAt": null,
  "expiredUrl": null,
  "password": null,
  "externalId": null,
  "identifier": null,
  "trackConversion": false,
  "proxy": false,
  "title": null,
  "description": null,
  "image": null,
  "video": null,
  "utm_source": null,
  "utm_medium": null,
  "utm_campaign": null,
  "utm_term": null,
  "utm_content": null,
  "rewrite": false,
  "doIndex": false,
  "ios": null,
  "android": null,
  "geo": null,
  "userId": null,
  "projectId": null,
  "publicStats": true,
  "clicks": 0,
  "lastClicked": null,
  "leads": 0,
  "sales": 0,
  "saleAmount": 0,
  "createdAt": "2024-10-03T21:16:48.809Z",
  "updatedAt": "2024-10-03T21:16:48.809Z",
  "comments": null,
  "tags": [],
  "shortLink": "https://dub.sh/example123",
  "tagId": null,
  "qrCode": "https://api.dub.co/qr?url=https://dub.sh/example123?qr=1",
  "workspaceId": null
}

fail:
422 {
  "error": {
    "code": "unprocessable_entity",
    "message": "Invalid destination URL",
    "doc_url": "https://dub.co/docs/api-reference/errors#unprocessable-entity"
  }
}

409 {
  "error": {
    "code": "conflict",
    "message": "Duplicate key: This short link already exists.",
    "doc_url": "https://dub.co/docs/api-reference/errors#conflict"
  }
}

429 {
  "error": {
    "code": "rate_limit_exceeded",
    "message": "Rate limited – you can only create up to 10 links per day without an account.",
    "doc_url": "https://dub.co/docs/api-reference/errors#rate-limit_exceeded"
  }
}

stats:
https://dub.sh/stats/BteWpBV

}
 */